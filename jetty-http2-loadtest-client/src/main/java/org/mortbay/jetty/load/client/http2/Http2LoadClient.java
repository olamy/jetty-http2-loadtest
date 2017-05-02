package org.mortbay.jetty.load.client.http2;

import com.beust.jcommander.JCommander;
import org.HdrHistogram.Histogram;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.mortbay.jetty.load.generator.HTTP2ClientTransportBuilder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.load.generator.listeners.CollectorInformations;
import org.mortbay.jetty.load.generator.listeners.report.GlobalSummaryListener;

import java.util.concurrent.TimeUnit;

/**
 *
 */
public class Http2LoadClient
{

    private static final Logger LOG = Log.getLogger( Http2LoadClient.class );

    private int port, runMinutes;

    private String host;

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
        ClientStartArgs clientStartArgs = new ClientStartArgs();
        new JCommander( clientStartArgs, args );
        http2LoadClient.port = clientStartArgs.getPort();
        http2LoadClient.host = clientStartArgs.getHost();
        http2LoadClient.runMinutes = clientStartArgs.getRunMinutes();
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
                .host( getHost() ) //
                .port( getPort() ).httpClientTransportBuilder( new HTTP2ClientTransportBuilder() ) //
                .resource( new Resource( "/simple" ) ) //
                .warmupIterationsPerThread( 2 ) //
                .iterationsPerThread( 2 ) //
                .threads( 5 ) //
                .usersPerThread( 5 ) //
                .resourceRate( 5000 ) //
                .runFor( runMinutes, TimeUnit.MINUTES ) //
                .scheme( "http" ) //
                .resourceListener( globalSummaryListener ) //
                .build();

        loadGenerator.begin().join();
        LOG.debug( "#run done" );
        stopServerMonitor();
        httpClient.stop();

        Histogram histogram = globalSummaryListener.getLatencyTimeHistogram().getIntervalHistogram();
        CollectorInformations collectorInformations = new CollectorInformations( histogram );
        LOG.info( "collectorInformations: {}", collectorInformations.toString( true ));

    }

    public void stopServer()
        throws Exception
    {
        httpClient.newRequest( "localhost", port ).path( "/exit" ).send();
    }

    public void startServerMonitor()
        throws Exception
    {
        httpClient.newRequest( "localhost", port ).path( "/start" ).send();
    }

    public void stopServerMonitor()
        throws Exception
    {
        String json =
            httpClient.newRequest( "localhost", port ).path( "/stop" ).send().getContentAsString();
        LOG.debug( "json monitor: {}", json );
    }

    public int getPort()
    {
        return port;
    }

    public void setPort( int port )
    {
        this.port = port;
    }

    public String getHost()
    {
        return host;
    }

    public void setHost( String host )
    {
        this.host = host;
    }
}
