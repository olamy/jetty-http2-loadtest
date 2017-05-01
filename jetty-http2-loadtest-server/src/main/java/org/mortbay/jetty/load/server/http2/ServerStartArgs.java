package org.mortbay.jetty.load.server.http2;

import com.beust.jcommander.Parameter;

public class ServerStartArgs
{

    @Parameter( names = { "--port", "-p" }, description = "Port to use (default 8080)" )
    private int port = 8080;

    @Parameter( names = { "--recycle-http-channels", "-rhc" }, description = "Recycle Http2 Channels (default false)" )
    private boolean recycleHttpChannels;

    public ServerStartArgs()
    {
        // no op
    }

    public int getPort()
    {
        return port;
    }

    public void setPort( int port )
    {
        this.port = port;
    }

    public boolean isRecycleHttpChannels()
    {
        return recycleHttpChannels;
    }

    public void setRecycleHttpChannels( boolean recycleHttpChannels )
    {
        this.recycleHttpChannels = recycleHttpChannels;
    }
}
