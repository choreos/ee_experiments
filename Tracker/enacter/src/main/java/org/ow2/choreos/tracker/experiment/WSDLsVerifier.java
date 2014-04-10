package org.ow2.choreos.tracker.experiment;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.ow2.choreos.chors.datamodel.Choreography;
import org.ow2.choreos.services.datamodel.DeployableService;
import org.ow2.choreos.services.datamodel.Service;
import org.ow2.choreos.tracker.Enacter;

public class WSDLsVerifier implements Runnable {
    
    private static Logger logger = Logger.getLogger(ChorVerifier.class);
    
    public static final int VERIFY_WSDLS_TIMEOUT = 10;

    private Enacter enacter;
    
    // output
    AtomicInteger servicesWorking = new AtomicInteger();
    long time;

    public WSDLsVerifier(Enacter enacter) {
        this.enacter = enacter;
    }

    /**
     * returns the amount of accessible WSDLs
     */
    @Override
    public void run() {
        long t0 = System.nanoTime();
        Choreography chor = enacter.getChoreography();
        int len = chor.getDeployableServices().size();
        logger.info("Verifying " + len + " services in enacter " + enacter.getId());
        for (DeployableService svc : chor.getDeployableServices()) {
            String wsdl = getWsdl(svc);
            WSDLChecker checker = new WSDLChecker(wsdl);
            if (checker.check()) {
                servicesWorking.incrementAndGet();
                logger.info("Tracker OK: " + wsdl);
            } else {
                logger.error("Tracker not accessible (enacter " + enacter.getId() + "): " + wsdl);
            }
        }
        long tf = System.nanoTime();
        time = tf - t0;
    }

    private String getWsdl(Service svc) {
        String uri = svc.getUris().get(0);
        String wsdl = uri.replaceAll("/$", "").concat("?wsdl");
        return wsdl;
    }

}
