package org.ow2.choreos.experiments.futuremarket;

import java.io.IOException;
import java.net.MalformedURLException;

import org.apache.log4j.Logger;
import org.ow2.choreos.chors.ChoreographyNotFoundException;
import org.ow2.choreos.chors.DeploymentException;
import org.ow2.choreos.chors.datamodel.Choreography;

public class FutureMarketExperiment {

	private static Choreography chor;
	
	private static Logger logger = Logger.getLogger(FutureMarketExperiment.class);

	static {
		LogConfigurator.configLog();
	}

	public static void main(final String[] args) throws 
			ChoreographyNotFoundException, MalformedURLException, DeploymentException {
		FutureMarketExperiment futureMarketExperiment = new FutureMarketExperiment();

		chor = futureMarketExperiment.enact();

		try {
			System.out.println("Press any key to start experiment:");
			System.in.read();
			futureMarketExperiment.runExperiment();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void runExperiment() throws IOException {
		AbstractLoadGenerator loadGenerator = new Frequency(chor);
		String[] args = { "chor", "60", "6000", "60", "1000", "0.85", "600" };
		loadGenerator.generateLoad(args, 2);
	}

	private Choreography enact() throws 
			ChoreographyNotFoundException, DeploymentException {
		logger.info("Enacting choreography...");
		final FutureMarketEnacter enacter = new FutureMarketEnacter();
		final Choreography chor = enacter.enact();
		return chor;
	}

}
