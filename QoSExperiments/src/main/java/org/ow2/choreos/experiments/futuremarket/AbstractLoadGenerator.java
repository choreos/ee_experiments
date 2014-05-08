package org.ow2.choreos.experiments.futuremarket;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.ow2.choreos.chors.datamodel.Choreography;

public abstract class AbstractLoadGenerator {

    protected static final int THREADS_TIMEOUT = 360;
    protected static final Logger GRAPH = Logger.getLogger("graphsLogger");
    protected static final Logger CONSOLE = Logger.getLogger(AbstractLoadGenerator.class);

    protected int portals, step;
	private Choreography choreography;
    
    public AbstractLoadGenerator(final Choreography chor) {
		this.choreography = chor;
	}

    public abstract void generateLoad(final String args[], int start) throws IOException;

    protected AbstractPortalProxy getPortalProxies() throws IOException {
        AbstractPortalProxy proxies = new PortalProxy(choreography);
        portals = proxies.size();
        return proxies;
    }
}