package org.ow2.choreos.tracker.experiment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.mail.EmailException;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.log4j.Logger;
import org.ow2.choreos.invoker.Invoker;
import org.ow2.choreos.invoker.InvokerBuilder;
import org.ow2.choreos.invoker.InvokerException;
import org.ow2.choreos.nodes.NodePoolManager;
import org.ow2.choreos.nodes.client.NodesClient;
import org.ow2.choreos.tracker.experiment.mail.ErrorMail;
import org.ow2.choreos.tracker.experiment.mail.FinishedMail;
import org.ow2.choreos.tracker.experiment.mail.ProgressMail;
import org.ow2.choreos.utils.CommandLineException;
import org.ow2.choreos.utils.LogConfigurator;
import org.ow2.choreos.utils.OSCommand;

public class ScalabilityExperiment {

//    public static final int NUM_EXECUTIONS = 10;
//    public static final int[] CHORS_QUANTITIES = new int[] { 1, 1, 1, 1, 1 };
//    public static final int[] CHORS_SIZES = new int[] { 200, 600, 1000, 1400, 1800 };
//    public static final int[] VMS_LIMITS = new int[] { 10, 30, 50, 70, 90 };
    
    public static final int NUM_EXECUTIONS = 2;
    public static final int[] CHORS_QUANTITIES = new int[] { 1, 1 };
    public static final int[] CHORS_SIZES = new int[] { 5, 10 };
    public static final int[] VMS_LIMITS = new int[] { 5, 5 };

    private static final int TIME_TO_WAIT_EE_START_MILLISEC = 1 * 60 * 1000;
    private static final String EE_URI = ExperimentConfiguration.get("EE_URI");
    private static final String EE_FOLDER = ExperimentConfiguration.get("EE_FOLDER");
    private static final String CLOUD_ACCOUNT = ExperimentConfiguration.get("CLOUD_ACCOUNT");

    private static Logger logger = Logger.getLogger(ScalabilityExperiment.class);

    private List<ExperimentDefinition> definitions;
    private OSCommand eeCommand;
    private ExperimentDefinition currentDefinition;

    public static void main(String[] args) {
        LogConfigurator.configLog();
        ScalabilityExperiment exps = new ScalabilityExperiment();
        exps.run();
    }

    private void run() {
        Report.setRepetitions(NUM_EXECUTIONS);
        createDefinitions();
        for (ExperimentDefinition def : definitions) {
            currentDefinition = def;
            executeDefinition(def);
        }
        sendFinishedMail();
    }

    private void createDefinitions() {
        definitions = new ArrayList<ExperimentDefinition>();
        for (int i = NUM_EXECUTIONS; i > 0; i--) {
            for (int s = 0; s < CHORS_SIZES.length; s++) {
                int size = CHORS_SIZES[s];
                int vmsLimit = VMS_LIMITS[s];
                int chorsQuantity = CHORS_QUANTITIES[s];
                definitions.add(new ExperimentDefinition(i, chorsQuantity, size, vmsLimit));
            }
        }
    }

    private void executeDefinition(ExperimentDefinition def) {
        logger.info("Executing definition " + def);
        startEE();
        configureEE();
        Experiment exp = new Experiment(def);
        exp.run();
        cleanAmazon();
        stopEE();
        sendProgressMail();
    }

    private void startEE() {
        logger.info("Starting EE...");
        String mvnExecCom = "mvn exec:java -o";
        try {
            eeCommand = new OSCommand(mvnExecCom, EE_FOLDER + "/EnactmentEngine");
            eeCommand.executeAssync();
        } catch (CommandLineException e) {
            logger.error("Could not start EE");
        }
        waitALittle();
        if (pingEE()) {
            logger.info("EE started");
        } else {
            abortExecution("EE not started");
        }
    }

    private void waitALittle() {
        try {
            Thread.sleep(TIME_TO_WAIT_EE_START_MILLISEC);
        } catch (InterruptedException e) {
            logger.error("Killed while sleeping!");
        }
    }

    private boolean pingEE() {
        EEPingTask task = new EEPingTask();
        Invoker<Integer> invoker = new InvokerBuilder<Integer>("EEPingTask", task, 10).trials(20)
                .pauseBetweenTrials(30).build();
        try {
            int status = invoker.invoke();
            return status == 404;
        } catch (InvokerException e) {
            return false;
        }
    }

    private void configureEE() {
        WebClient client = WebClient.create(EE_URI);
        client.type(MediaType.TEXT_PLAIN);
        client.path(CLOUD_ACCOUNT).path("nodes").path("vm_limit");
        String vmLimit = Integer.toString(currentDefinition.getVmLimit());
        client.put(vmLimit);
        logger.info("EE configured");
    }

    private void cleanAmazon() {
        logger.info("Destroying EC2 instances...");
        CleanAmazonTask task = new CleanAmazonTask();
        Invoker<Void> invoker = new InvokerBuilder<Void>("CleanAmazonTask", task, 5).timeUnit(TimeUnit.MINUTES)
                .trials(3).pauseBetweenTrials(1).build();
        try {
            invoker.invoke();
            logger.info("EC2 instances destroyed");
        } catch (InvokerException e) {
            abortExecution("Could not destroy all EC2 instances!");
        }
    }

    private void stopEE() {
        eeCommand.killProcess();
        logger.info("EE killed");
    }

    private void sendProgressMail() {
        logger.info("Sending progress email");
        ProgressMail mail = new ProgressMail(currentDefinition);
        try {
            mail.send();
        } catch (EmailException e) {
            logger.warn("Not possible to send progress email");
        }
        logger.info("Progress email sent");
    }

    private class EEPingTask implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            WebClient client = WebClient.create(EE_URI);
            client.path(CLOUD_ACCOUNT).path("nodes").path("41");
            Response response = client.get();
            return response.getStatus();
        }
    }

    private void abortExecution(String error) {
        logger.error("Aborting execution: " + error);
        sendErrorMail(error);
        System.exit(-1);
    }

    private void sendErrorMail(String error) {
        logger.info("Sending error email");
        ErrorMail mail = new ErrorMail(error);
        try {
            mail.send();
        } catch (EmailException e) {
            logger.warn("Not possible to send error email");
        }
        logger.info("Error email sent");
    }

    private class CleanAmazonTask implements Callable<Void> {
        @Override
        public Void call() throws Exception {
            NodePoolManager npm = new NodesClient(EE_URI, CLOUD_ACCOUNT);
            npm.destroyNodes();
            return null;
        }
    }

    private void sendFinishedMail() {
        logger.info("Sending finished email");
        FinishedMail mail = new FinishedMail();
        try {
            mail.send();
        } catch (EmailException e) {
            logger.warn("Not possible to send finished email");
        }
        logger.info("Finished email sent");
    }
}
