package org.mortbay.jetty.load.server.http2;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnection;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.toolchain.perf.PlatformMonitor;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 */
public class Http2Server
{

    private static final Logger LOG = Log.getLogger( Http2Server.class );

    private int port;

    private boolean recycleHttpChannels;

    Server server;

    ServerConnector serverConnector;

    private static PlatformMonitor MONITOR = new PlatformMonitor();

    private static PlatformMonitor.Start START;

    private static PlatformMonitor.Stop STOP;

    public static void main( String[] args )
        throws Exception
    {
        Http2Server http2Server = new Http2Server();
        parseArguments( args, http2Server );
        http2Server.run();
    }

    public static void parseArguments( String[] args, Http2Server http2Server )
    {
        ServerStartArgs serverStartArgs = new ServerStartArgs();
        new JCommander( serverStartArgs, args );
        http2Server.port = serverStartArgs.getPort();
        http2Server.recycleHttpChannels = serverStartArgs.isRecycleHttpChannels();
    }


    public void run()
        throws Exception
    {
        server = new Server();
        serverConnector = new ServerConnector( server, //
                                               new H2CConnectionFactory( new HttpConfiguration() ) //
                                                   .recycleHttpChannels( this.recycleHttpChannels ) );
        LOG.info( "recycleHttpChannels: {}", recycleHttpChannels );
        serverConnector.setPort( this.port );
        server.addConnector( serverConnector );
        ServletHandler servletHandler = new ServletHandler();
        servletHandler.addServletWithMapping( SimpleServlet.class, "/simple" );
        servletHandler.addServletWithMapping( ExitServlet.class, "/exit" );
        servletHandler.addServletWithMapping( MonitorStartServlet.class, "/start" );
        //servletHandler.addServletWithMapping( MonitorStopServlet.class, "/stop" );
        servletHandler.addServletWithMapping( new ServletHolder( new MonitorStopServlet( this ) ), "/stop" );
        server.setHandler( servletHandler );
        server.start();
    }


    static class H2CConnectionFactory
        extends HTTP2CServerConnectionFactory
    {
        private boolean recycleHttpChannels;

        public H2CConnectionFactory( HttpConfiguration httpConfiguration )
        {
            super( httpConfiguration );
        }

        @Override
        public Connection newConnection( Connector connector, EndPoint endPoint )
        {
            HTTP2ServerConnection connection = (HTTP2ServerConnection) super.newConnection( connector, endPoint );
            connection.setRecycleHttpChannels( recycleHttpChannels );
            return connection;
        }

        public H2CConnectionFactory recycleHttpChannels( boolean recycleHttpChannels )
        {
            this.recycleHttpChannels = recycleHttpChannels;
            return this;
        }
    }


    public static class SimpleServlet
        extends HttpServlet
    {
        @Override
        protected void doGet( HttpServletRequest req, HttpServletResponse response )
            throws ServletException, IOException
        {
            LOG.debug( "doGet" );
        }


    }

    public static class ExitServlet
        extends HttpServlet
    {
        @Override
        protected void doGet( HttpServletRequest req, HttpServletResponse response )
            throws ServletException, IOException
        {
            LOG.info( "ExitServlet#get -> exit" );
            System.exit( 0 );
        }
    }

    public static class MonitorStartServlet
        extends HttpServlet
    {
        @Override
        protected void doGet( HttpServletRequest req, HttpServletResponse response )
            throws ServletException, IOException
        {
            LOG.info( "MonitorStartServlet#get" );
            System.gc();
            START = MONITOR.start();
        }
    }

    public static class MonitorStopServlet
        extends HttpServlet
    {

        Http2Server http2Server;

        public MonitorStopServlet( Http2Server http2Server )
        {
            this.http2Server = http2Server;
        }

        @Override
        protected void doGet( HttpServletRequest req, HttpServletResponse response )
            throws ServletException, IOException
        {
            LOG.info( "MonitorStopServlet#get" );
            STOP = MONITOR.stop();

            Map<String, Object> run = new LinkedHashMap<>();
            Map<String, Object> config = new LinkedHashMap<>();
            run.put( "config", config );
            config.put( "recycleHttpChannels", http2Server.recycleHttpChannels );
            config.put( "cores", START.cores );
            config.put( "totalMemory", new Measure( START.gibiBytes( START.totalMemory ), "GiB" ) );
            config.put( "os", START.os );
            config.put( "jvm", START.jvm );
            config.put( "totalHeap", new Measure( START.gibiBytes( START.heap.getMax() ), "GiB" ) );
            config.put( "date", new Date( START.date ).toString() );
            Map<String, Object> results = new LinkedHashMap<>();
            run.put( "results", results );
            results.put( "cpu", new Measure( STOP.percent( STOP.cpuTime, STOP.time ) / START.cores, "%" ) );
            results.put( "jitTime", new Measure( STOP.jitTime, "ms" ) );
            Map<String, Object> latency = new LinkedHashMap<>();
            //results.put( "latency", latency );
            //latency.put( "min", new Measure( convert( histogram.getMinValue() ), "\u00B5s" ) );
            //latency.put( "p50", new Measure( convert( histogram.getValueAtPercentile( 50D ) ), "\u00B5s" ) );
            //latency.put( "p99", new Measure( convert( histogram.getValueAtPercentile( 99D ) ), "\u00B5s" ) );
            //latency.put( "max", new Measure( convert( histogram.getMaxValue() ), "\u00B5s" ) );
            //Map<String, Object> threadPool = new LinkedHashMap<>();
            //results.put( "threadPool", threadPool );
            //threadPool.put( "tasks", this.threadPool.getTasks() );
            //threadPool.put( "queueSizeMax", this.threadPool.getMaxQueueSize() );
            //threadPool.put( "activeThreadsMax", this.threadPool.getMaxActiveThreads() );
            //threadPool.put("queueLatencyAverage", new Measure(TimeUnit.NANOSECONDS.toMillis(this.threadPool.getAverageQueueLatency()), "ms"));
            //threadPool.put("queueLatencyMax", new Measure(TimeUnit.NANOSECONDS.toMillis(this.threadPool.getMaxQueueLatency()), "ms"));
            //threadPool.put("taskTimeAverage", new Measure(TimeUnit.NANOSECONDS.toMillis(this.threadPool.getAverageTaskLatency()), "ms"));
            //threadPool.put("taskTimeMax", new Measure(TimeUnit.NANOSECONDS.toMillis(this.threadPool.getMaxTaskLatency()), "ms"));
            Map<String, Object> gc = new LinkedHashMap<>();
            results.put( "gc", gc );
            gc.put( "youngCount", STOP.youngCount );
            gc.put( "youngTime", new Measure( STOP.youngTime, "ms" ) );
            gc.put( "oldCount", STOP.oldCount );
            gc.put( "oldTime", new Measure( STOP.oldTime, "ms" ) );
            gc.put( "youngGarbage", new Measure( STOP.mebiBytes( STOP.edenBytes + STOP.survivorBytes ), "MiB" ) );
            gc.put( "oldGarbage", new Measure( STOP.mebiBytes( STOP.tenuredBytes ), "MiB" ) );

            new ObjectMapper() //
                .findAndRegisterModules() //
                .enable( SerializationFeature.INDENT_OUTPUT ) //
                .writeValue( response.getWriter(), run );
        }
    }

    public int getPort()
    {
        return port;
    }

    public void setPort( int port )
    {
        this.port = port;
    }

    private static class Measure
        extends HashMap<String, Object>
    {
        public Measure( Object value, String unit )
        {
            super( 2 );
            put( "value", value );
            put( "unit", unit );
        }
    }
}
