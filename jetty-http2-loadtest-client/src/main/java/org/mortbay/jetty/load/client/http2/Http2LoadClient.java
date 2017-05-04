package org.mortbay.jetty.load.client.http2;

import com.beust.jcommander.JCommander;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        Http2LoadClient http2LoadClient = new Http2LoadClient();
        parseArguments( args, http2LoadClient );
        http2LoadClient.run();
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
        stopServerMonitor();

        Histogram histogram = globalSummaryListener.getLatencyTimeHistogram().getIntervalHistogram();
        CollectorInformations collectorInformations = new CollectorInformations( histogram );
        LOG.info( "collectorInformations: {}", collectorInformations.toString( true ) );
        LOG.info( "1xxx: " + globalSummaryListener.getResponses1xx() );
        LOG.info( "2xxx: " + globalSummaryListener.getResponses2xx() );
        LOG.info( "3xxx: " + globalSummaryListener.getResponses3xx() );
        LOG.info( "4xxx: " + globalSummaryListener.getResponses4xx() );
        LOG.info( "5xxx: " + globalSummaryListener.getResponses5xx() );
        LOG.info( "loadGenerator.config: {}", loadGenerator.getConfig().toString() );

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

    public void stopServer()
        throws Exception
    {
        httpClient.newRequest( "localhost", startArgs.getPort() ).path( "/exit" ).send();
    }

    public void startServerMonitor()
        throws Exception
    {
        httpClient.newRequest( "localhost", startArgs.getPort() ).path( "/start" ).send();
    }

    public void stopServerMonitor()
        throws Exception
    {
        String json = httpClient.newRequest( "localhost", startArgs.getPort() ) //
            .path( "/stop" ) //
            .send() //
            .getContentAsString();
        LOG.info( "json monitor: {}", json );
        Path result = Paths.get( "./result.json" );
        Files.deleteIfExists( result );

        try (BufferedWriter bufferedWriter = Files.newBufferedWriter( result ))
        {
            bufferedWriter.write( json );
        }
    }

}
