/*
 * 
 * agent code to implement a simple negotiation algorithm
 * for distributed congestion management
 * 
 */
package LabEx;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.Property;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import static java.lang.Math.abs;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CurtailAgent extends Agent {

    /*initialization of the agent*/
    private AID[] neighboors;
    private AID coordinator;
    private int[] neighId;
    private int noOfNeighs;
    private int agentId;
    private double b_cost;
    private double c_cost;
    private double Pmin;
    private double Pmax;
    //P and V measurements, taken from the Matlab Agent
//    double FeederVoltage;
//    double Pload;
    iterData iter = new iterData();
    iterData[] neighData;
    iterData[] tempData;
    iterData tempIter;
    final int MAX_ITER = 1000;
    private int iterationsCompleted = 0;
    private int nextIterationsCompleted = 0;
    boolean nextMessage = false;
    boolean currentMessage = false;
    boolean converged = false;
    double tolerance = 0.001;
    double tempFitness = 0;
    double initialPower = 140;
    PrintWriter writer;
    Behaviour receiveBehaviour;
    Behaviour handleData;
    Behaviour listen;

    protected void setup() {
        //System.out.println("Hello!  "+ getName()+" is ready.");
        /*Initialization phase, neiboors discovery and DF registration */
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            noOfNeighs = Integer.parseInt(args[0].toString());
            neighId = new int[noOfNeighs];
            //System.out.println(getLocalName()+"  "+args.length);
            for (int i = 1; i < noOfNeighs + 1; i++) {
                neighId[i - 1] = Integer.parseInt(args[i].toString()) + 1;
                //System.out.println("Agent: " + getLocalName()+ " has neighboor: " + neighId[i]);
            }
            Pmin = Double.parseDouble(args[noOfNeighs + 1].toString());
            Pmax = Double.parseDouble(args[noOfNeighs + 2].toString());
            b_cost = Double.parseDouble(args[noOfNeighs + 3].toString());
            c_cost = Double.parseDouble(args[noOfNeighs + 4].toString());
        }
        System.out.println(getLocalName() + " found parameters: " + Pmin + " " + Pmax + " " + b_cost + " " + c_cost);

        //Extract agent id
        agentId = Integer.parseInt(getLocalName().replaceAll("[a-zA-Z]", ""));
        iter.agentId = agentId;
        iter.timestamp = 0;
        //System.out.println("Agent: " + getLocalName()+ " has Id: " + agentId);

        // Register the agent in the yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setName("Load" + agentId);
        sd.setType("Curtailment");

        //assign neighboors as properties on the DF
        for (int i = 0; i < neighId.length; i++) {
            Property agentProperty = new Property();
            agentProperty.setName("Neighboor " + (i + 1));
            agentProperty.setValue(neighId[i]);
            sd.addProperties(agentProperty);
        }
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // one object containing info for each neighboor and one for temporary storage
        neighData = new iterData[neighId.length];
        tempData = new iterData[neighId.length];
        //objects must be constructed independently --> to be better implemented
        for (int i = 0; i < neighId.length; i++) {
            neighData[i] = new iterData();
            tempData[i] = new iterData();
        }

        /* discover neighboors and coordinator */
        addBehaviour(new WakerBehaviour(this, 30000) {
            protected void handleElapsedTimeout() {
                addBehaviour(new FindNeighboors());
            }
        });

        //write data in file
        try {
            writer = new PrintWriter(getLocalName() + ".txt", "UTF-8");
        } catch (FileNotFoundException ex) {
            Logger.getLogger(CurtailAgent.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(CurtailAgent.class.getName()).log(Level.SEVERE, null, ex);
        }
        writer.println("Record keeping!!");

        /*enviroment of the agent*/
        //behaviors
        receiveBehaviour = new receiveFromNeighboors();
        listen = new listenState();

        System.out.println(getLocalName() + " waiting to receive from coordinator......");
        addBehaviour(listen);

//        addBehaviour(new WakerBehaviour(this, 10000) {
//            protected void handleElapsedTimeout() {
//                addBehaviour(new startNegotiation());
//            }
//        });
    }

    public class receiveFromNeighboors extends CyclicBehaviour {

        public void action() {
            boolean stopNeg = false;
            ACLMessage msg = receive();
            if (msg != null) {
                // Message received. Process it
                if (msg.getContent().toString().contains("STOP")) {
                    //coordinator asks to stop negotiation
                    stopNeg = true;

                } else {
                    //continue with negotiation
                    tempIter = new iterData(msg.getContent().toString());
                    //find in which object does the sender belong and update its object and check timestamp
                    boolean found = false;
                    int i = 0;
                    while ((!found) && (i < neighId.length)) {
                        if (neighId[i] == tempIter.agentId) {
                            found = true;
                            if (tempIter.timestamp > iter.timestamp) {
                                //received newer message
                                System.out.println(getLocalName() + " received newer message!!  " + iter.timestamp + "  " + tempIter.timestamp + " from " + tempIter.agentId);
                                tempData[i].DesirializeVals(tempIter.SerializeVals());
                                nextIterationsCompleted++;
                                nextMessage = true;
                            } else if (tempIter.timestamp == iter.timestamp) {
                                //received good message
                                iterationsCompleted++;
                                System.out.println(getLocalName() + " received good message!!  " + iter.timestamp + "  " + tempIter.timestamp + " from " + tempIter.agentId + " --> " + iterationsCompleted);
                                neighData[i].DesirializeVals(tempIter.SerializeVals());
                                currentMessage = true;
                            } else {
                                System.out.println(getLocalName() + " problem " + iter.timestamp + "  " + tempIter.timestamp + " from " + tempIter.agentId);
                            }
                        }
                        i++;
                    }

                }
                //message handling
                if (currentMessage) {
                    if (iterationsCompleted == noOfNeighs) {
                        System.out.println(getLocalName() + " received from all neighboors for iteration: " + iter.timestamp);
                        updateVals();
                        sendVals();
                        currentMessage = false;
                        iterationsCompleted = 0;
                        if (nextIterationsCompleted > 0) {
                            //copy newer messages buffer to current buffer
                            for (int k = 0; k < noOfNeighs; k++) {
                                neighData[k].DesirializeVals(tempData[k].SerializeVals());
                                tempData[k].clear();
                            }
                            iterationsCompleted = nextIterationsCompleted;
                            nextIterationsCompleted = 0;
                        }

                    } else {
                        //wait to receive from every neighboor
                        currentMessage = false;

                    }

                } else if (nextMessage) {
                    nextMessage = false;

                } else if (stopNeg) {
                    System.out.println(getLocalName() + " stoped. Final vector: " + iter.SerializeVals());
                    //stop receiveFromNeighboors() behaviour
                    removeBehaviour(receiveBehaviour);
                    //inform coordinator
                    addBehaviour(new informCoordinator());

                }

                if (iter.timestamp > MAX_ITER) {
                    System.out.println(getLocalName() + " finished. Final vector: " + iter.SerializeVals());
                    //stop receiveFromNeighboors() behaviour
                    removeBehaviour(receiveBehaviour);
                    //inform coordinator
                    addBehaviour(new informCoordinator());
                }

                //if agent has converged inform coordinator
                if ((iter.fitness - tempFitness) < tolerance) {
                    converged = true;
                    System.out.println(getLocalName() + " seems to have converged at iteration: " + iter.timestamp + " " + (iter.fitness - tempFitness));
                }

            } else {
                block();
            }
        }
    }

    public void sendVals() {

        //System.out.println(myAgent.getLocalName()+ " Sends message!! ");
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        for (int i = 0; i < neighboors.length; i++) {
            //multiple receivers
            msg.addReceiver(new AID(neighboors[i].getLocalName(), AID.ISLOCALNAME));
        }
        msg.setContent(iter.SerializeVals());
        System.out.println(getLocalName() + " Sends message at iterarion: " + iter.timestamp);
        send(msg);

    }

    public class startNegotiation extends OneShotBehaviour {

        public void action() {

            //calculate initial fitness
            iter.fitness = fitnessF(initialPower);
            //record keeping
            iter.power = initialPower;
            sendVals();
            writer.println(iter.SerializeVals());
            addBehaviour(receiveBehaviour);

        }
    }

    public void updateVals() {

        System.out.println(getLocalName() + " updates values at iteration " + iter.timestamp);
        double sum_FP = 0; // F_nei.*P_nei
        double sum_P = 0;  // P_nei
        final double Ts = 0.1;
        //calculation of next value based on the local replicator equation model
        for (int i = 0; i < noOfNeighs; i++) {
            sum_P = sum_P + neighData[i].power;
            sum_FP = sum_FP + (neighData[i].power) * (neighData[i].fitness);
        }
        tempFitness = iter.fitness;
        try {
            iter.power = (iter.power * fitnessF(iter.power) + Ts) / ((sum_FP / sum_P) + Ts);
        } catch (Exception e) {
            System.out.println("division by zero exception");
            System.out.println("inside-catch");
            iter.power = 1;
        }
        iter.fitness = fitnessF(iter.power);
        if (iter.fitness < 0) {
            System.out.println("Negative fitness!! Must increase Beta parameter...");
        }
        iter.timestamp++;
        writer.println(iter.SerializeVals());

    }

    public double fitnessF(double power) {

        final double B = 75000;
        final double m = 50;
        double result;

        if (power < Pmin) {
            result = m * (Pmin - power) + B - (b_cost + 2 * c_cost * Pmin);
        } else if ((Pmin <= power) && (power <= Pmax)) {
            result = B - (b_cost + 2 * c_cost * power);
        } else {
            result = (-1) * m * (power - Pmax) + B - (b_cost + 2 * c_cost * Pmax);
        }
        return result;

    }

    public class FindNeighboors extends OneShotBehaviour {

        public void action() {
            // find AIDs of neighboors
            neighboors = new AID[neighId.length];
            for (int i = 0; i < neighId.length; i++) {
                try {
                    //System.out.println("LoadAgent"+neighId[i]);
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setName("Load" + neighId[i]);
                    template.addServices(sd);
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    //System.out.println(result+ "  " + myAgent.getLocalName());
                    if (result.length > 0) {
                        neighboors[i] = result[0].getName();
                    }
                    //coordinator
                    template.clearAllServices();
                    sd.setName("Coordinator agent");
                    sd.setType("Agent coordination");
                    template.addServices(sd);
                    result = DFService.search(myAgent, template);
                    if (result.length > 0) {
                        coordinator = result[0].getName();
                    }

                } catch (FIPAException ex) {
                    Logger.getLogger(CurtailAgent.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            //debugging
            String localAgents = getLocalName() + " found load agents:  ";
            for (int i = 0; i < neighboors.length; i++) {
                localAgents += " " + neighboors[i].getLocalName();
            }
            System.out.println(localAgents + "!");

        }
    }

    public class iterData {

        //object containing all information for each iteration
        public int timestamp;
        public double power;
        public double fitness;
        public int agentId;

        public iterData() {
            this.timestamp = 0;
            this.power = 0;
            this.fitness = 0;
            this.agentId = 0;
        }

        public iterData(int timestamp, double power, double fitness, int agentId) {

            this.timestamp = timestamp;
            this.power = power;
            this.fitness = fitness;
            this.agentId = agentId;
        }

        public iterData(String val) {

            String[] test = val.split("/");
            this.timestamp = Integer.parseInt(test[0]);
            this.power = Double.parseDouble(test[1]);
            this.fitness = Double.parseDouble(test[2]);
            this.agentId = Integer.parseInt(test[3]);

        }

        public String SerializeVals() {

            return timestamp + "/" + power + "/" + fitness + "/" + agentId;
        }

        public void DesirializeVals(String val) {

            String[] test = val.split("/");
            this.timestamp = Integer.parseInt(test[0]);
            this.power = Double.parseDouble(test[1]);
            this.fitness = Double.parseDouble(test[2]);
            this.agentId = Integer.parseInt(test[3]);
        }

        public void clear() {
            this.timestamp = 0;
            this.power = 0;
            this.fitness = 0;
            this.agentId = 0;
        }
    }

    public class informCoordinator extends OneShotBehaviour {

        public void action() {
            //send message to coordinator
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(coordinator);
            msg.setLanguage("English");
            msg.setOntology("Negotiate");
            msg.setContent(getLocalName() + "converged/" + iter.SerializeVals());
            send(msg);

            //go to listen behaviour
            addBehaviour(listen);

        }
    }

    public class listenState extends CyclicBehaviour {

        public void action() {
            //wait for command from coordinator
            ACLMessage msg = receive();
            if (msg != null) {
                // Message received. Process it
                String val = msg.getContent().toString();
                if (val.contains("negotiate")) {
                    String[] temp = val.split("/");
                    removeBehaviour(listen);
                    initialPower = Double.parseDouble(temp[1]);
                    addBehaviour(new startNegotiation());
                }
            } else {
                block();
            }
        }
    }

    public void takeDown() {

        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        //very important to close the connection with the load and the db
        writer.close();
        // Printout a dismissal message
        System.out.println("Agent " + getAID().getName() + " terminating.");
        doDelete();

    }
}
