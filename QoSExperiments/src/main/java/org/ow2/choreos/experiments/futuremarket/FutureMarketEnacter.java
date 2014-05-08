package org.ow2.choreos.experiments.futuremarket;

import org.ow2.choreos.chors.ChoreographyNotFoundException;
import org.ow2.choreos.chors.DeploymentException;
import org.ow2.choreos.chors.client.EEClient;
import org.ow2.choreos.chors.datamodel.Choreography;
import org.ow2.choreos.chors.datamodel.ChoreographySpec;

public class FutureMarketEnacter {

	private static final String EE_HOST = "http://localhost:9100/enactmentengine/";

	private ExperimentSupport experimentSupport = new ExperimentSupport();

	public Choreography enact() throws 
			ChoreographyNotFoundException, DeploymentException {

		final ChoreographySpec chorSpec = experimentSupport.getChorSpec();
		
		

		final EEClient eeClient = new EEClient(EE_HOST);
		final String chorId = eeClient.createChoreography(chorSpec);
		final Choreography chor = eeClient.deployChoreography(chorId);

		return chor;
	}
}
