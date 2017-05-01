package org.mortbay.jetty.load.server.http2;

import com.beust.jcommander.JCommander;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnection;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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
        serverConnector.setPort( this.port );
        server.addConnector( serverConnector );
        ServletHandler servletHandler = new ServletHandler();
        servletHandler.addServletWithMapping( SimpleServlet.class, "/simple" );
        servletHandler.addServletWithMapping( SimpleServlet.class, "/exit" );
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
            LOG.info( "exit" );
            System.exit( 0 );
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
}
