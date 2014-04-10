package org.ow2.choreos.tracker.experiment;

import java.net.MalformedURLException;

import org.apache.log4j.Logger;
import org.ow2.choreos.services.datamodel.DeployableService;
import org.ow2.choreos.tracker.Enacter;

class ChorVerifier implements Runnable {

    private static Logger logger = Logger.getLogger(ChorVerifier.class);

    private Enacter enacter;
    
    // output
    boolean ok = false;
    long time;
    

    ChorVerifier(Enacter enacter) {
        this.enacter = enacter;
    }

    /**
     * Returns true if the choreography is returning its expected value.
     */
    @Override
    public void run() {
        logger.info("Verifying Enacter#" + enacter.getId());
        DeployableService tracker0 = enacter.getChoreography().getMapOfDeployableServicesBySpecNames().get("tracker0");
        logger.info("Tracker0 of enacter " + enacter.getId() + ":" + tracker0.getUris());
        try {
            long t0 = System.nanoTime();
            ok = enacter.verifyAnswer();
            long tf = System.nanoTime();
            time = tf - t0;
            if (ok) {
                logger.info("All services working on enacter " + enacter.getId());
            } 
        } catch (MalformedURLException e) {
            logger.error("Ops, this problem should not occur with Enacter#" + enacter.getId());
            ok = false;
        }
        logger.info("Enacter#" + enacter.getId() + " ok: " + ok);
    }

}
