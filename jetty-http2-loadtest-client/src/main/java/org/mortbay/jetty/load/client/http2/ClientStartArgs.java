package org.mortbay.jetty.load.client.http2;

import com.beust.jcommander.Parameter;

public class ClientStartArgs
{

    @Parameter( names = { "--port", "-p" }, description = "Port to use (default 8080)" )
    private int port = 9090;

    @Parameter( names = { "--host", "-h" }, description = "Host to use (default localhost)" )
    private String host = "localhost";

    @Parameter( names = { "--run-minutes", "-rm" }, description = "Minutes to run (default 3)" )
    private int runMinutes = 3;

    public ClientStartArgs()
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

    public String getHost()
    {
        return host;
    }

    public void setHost( String host )
    {
        this.host = host;
    }

    public int getRunMinutes()
    {
        return runMinutes;
    }

    public void setRunMinutes( int runMinutes )
    {
        this.runMinutes = runMinutes;
    }
}
