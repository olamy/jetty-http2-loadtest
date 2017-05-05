package org.mortbay.jetty.load.client.http2;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.HdrHistogram.Histogram;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.mortbay.jetty.load.generator.HTTP2ClientTransportBuilder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.load.generator.listeners.CollectorInformations;
import org.mortbay.jetty.load.generator.listeners.report.GlobalSummaryListener;
import org.mortbay.jetty.load.generator.starter.LoadGeneratorStarterArgs;

import java.io.BufferedWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class Http2LoadClient
{

    private static final Logger LOG = Log.getLogger( Http2LoadClient.class );

    private LoadGeneratorStarterArgs startArgs;

    HttpClient httpClient;

    public static void main( String[] args )
        throws Exception
    {
        LOG.debug( "start with args: {}", Arrays.asList( args ) );
        Http2LoadClient http2LoadClient = new Http2LoadClient();
        parseArguments( args, http2LoadClient );
        try
        {
            http2LoadClient.run();
        }
        catch ( Throwable e )
        {
            e.printStackTrace();
        }
        LOG.debug( "#main done" );
        System.exit( 0 );
    }


    static void parseArguments( String[] args, Http2LoadClient http2LoadClient )
    {
        http2LoadClient.startArgs = new LoadGeneratorStarterArgs();
        new JCommander( http2LoadClient.startArgs, args );
    }

    public void run()
        throws Exception
    {
        LOG.debug( "http2LoadClient#run" );
        GlobalSummaryListener globalSummaryListener = new GlobalSummaryListener();
        httpClient = new HttpClient( new HttpClientTransportOverHTTP2( new HTTP2Client() ), null );
        httpClient.start();
        startServerMonitor();
        LoadGenerator loadGenerator = //
            new LoadGenerator.Builder() //
                .host( startArgs.getHost() == null ? "localhost" : startArgs.getHost() ) //
                .port( startArgs.getPort() ) //
                .httpClientTransportBuilder( new HTTP2ClientTransportBuilder() ) //
                .resource( new Resource( "/simple" ) ) //
                .warmupIterationsPerThread( 2 ) //
                .iterationsPerThread( startArgs.getRunIteration() < 1 ? 2 : startArgs.getRunIteration() ) //
                .threads( startArgs.getThreads() < 1 ? 5 : startArgs.getThreads() ) //
                .usersPerThread( startArgs.getUsers() < 1 ? 5 : startArgs.getUsers() ) //
                .resourceRate( startArgs.getTransactionRate() <= 1 ? 5000 : startArgs.getTransactionRate() ) //
                .runFor( startArgs.getRunningTime(), TimeUnit.MINUTES ) //
                .scheme( "http" ) //
                .resourceListener( globalSummaryListener ) //
                .build();

        try
        {
            LOG.info( "#run start" );
            loadGenerator.begin().join();
            LOG.info( "#run done" );
        }
        catch ( Throwable e )
        {
            LOG.info( "fail to run load test", e );
        }
        String json = stopServerMonitor();

        ObjectMapper objectMapper = new ObjectMapper() //
            .findAndRegisterModules() //
            .enable( SerializationFeature.INDENT_OUTPUT );

        Map<String, Object> result = objectMapper.readValue( json, Map.class );

        Map<String, Object> responses = new HashMap<>();
        responses.put( "1xxx", Long.toString( globalSummaryListener.getResponses1xx().intValue() ) );
        responses.put( "2xxx", Long.toString( globalSummaryListener.getResponses2xx().intValue() ) );
        responses.put( "3xxx", Integer.toString( globalSummaryListener.getResponses3xx().intValue() ) );
        responses.put( "4xxx", Integer.toString( globalSummaryListener.getResponses4xx().intValue() ) );
        responses.put( "5xxx", Integer.toString( globalSummaryListener.getResponses5xx().intValue() ) );
        result.put( "responses", responses );

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ss z" );

        Histogram histogram = globalSummaryListener.getLatencyTimeHistogram().getIntervalHistogram();
        CollectorInformations collectorInformations = new CollectorInformations( histogram );
        Map<String, Object> latency = new HashMap<>();
        latency.put( "totalCount", collectorInformations.getTotalCount() );
        latency.put( "minValue", TimeUnit.NANOSECONDS.toMillis( collectorInformations.getMinValue() ) );
        latency.put( "meanValue", TimeUnit.NANOSECONDS.toMillis( Math.round( collectorInformations.getMean() ) ) );
        latency.put( "maxValue", TimeUnit.NANOSECONDS.toMillis( collectorInformations.getMaxValue() ) );
        latency.put( "value50", TimeUnit.NANOSECONDS.toMillis( collectorInformations.getValue50() ) );
        latency.put( "value90", TimeUnit.NANOSECONDS.toMillis( collectorInformations.getValue90() ) );
        latency.put( "start", simpleDateFormat.format( collectorInformations.getStartTimeStamp() ) );
        latency.put( "end", simpleDateFormat.format( collectorInformations.getEndTimeStamp() ) );
        latency.put( "loadGenerator.config", loadGenerator.getConfig().toString() );

        result.put( "latency", latency );

        Path resultJson = Paths.get( "./result.json" );
        Files.deleteIfExists( resultJson );
        StringWriter stringWriter = new StringWriter();
        objectMapper.writeValue( stringWriter, result );
        ;
        json = stringWriter.toString();
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter( resultJson ))
        {
            objectMapper.writeValue( bufferedWriter, json );
        }

        LOG.info( "json monitor: {}", json );

        String stopServer = startArgs.getParams().get( "stopServer" );
        if ( Boolean.parseBoolean( stopServer ) )
        {
            stopServer();
            LOG.info( "server stopped" );
        }
        else
        {
            LOG.info( "not stopping server" );
        }

        httpClient.stop();
    }

    public void startServerMonitor()
        throws Exception
    {
        httpClient.newRequest( "localhost", startArgs.getPort() ).path( "/start" ).send();
    }

    public String stopServerMonitor()
        throws Exception
    {
        String json = httpClient.newRequest( "localhost", startArgs.getPort() ) //
            .path( "/stop" ) //
            .send() //
            .getContentAsString();

        return json;

    }

    public void stopServer()
        throws Exception
    {
        httpClient.newRequest( "localhost", startArgs.getPort() ).path( "/exit" ).send( null );
    }

}
