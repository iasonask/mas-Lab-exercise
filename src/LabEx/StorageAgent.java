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
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class StorageAgent extends Agent {

    final int NAgents = 4;
    private AID[] AgentAIDs = new AID[NAgents];
    private int AgentsUp = 0;
    Behaviour waitForAgents;
    Behaviour receiveAgents;
    Behaviour operate;
    Behaviour decision;
    SetCellValues appletVal;
    private double DSO = 0;
    private double PV = 0;
    private double Load = 0;
    private double initialLoad = 0;
    final double SOC = 0.3;
    private boolean loadFlexibility;
    private boolean initialization = true;
    private double Pbat = 0;

    protected void setup() {

        //Register Agent on the DF
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setName("Storage Agent");
        sd.setType("Storage");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        waitForAgents = new waitAgents();
        receiveAgents = new receiveFromAgents();
        operate = new opMicrogrid();
        decision = new takeDecision();

        addBehaviour(waitForAgents);

        //add a ticker behavior to monitor PV production, disable PLay button in the meanwhile
        addBehaviour(new TickerBehaviour(this, 5000) {
            protected void onTick() {
                if (AgentsUp == 0) {
                    appletVal.playButton.setEnabled(false);
                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(new AID(AgentAIDs[2].getLocalName(), AID.ISLOCALNAME));
                    msg.setContent("Inform!!!");
                    send(msg);
                    String stripedValue = "";
                    msg = receive();
                    if (msg != null) {
                        // Message received. Process it
                        String val = msg.getContent().toString();
                        stripedValue = (val.replaceAll("[\\s+a-zA-Z :]", ""));
                        //PV
                        if (val.contains("PV")) {
                            System.out.println(val);
                            PV = Double.parseDouble(stripedValue);
                            appletVal.playButton.setEnabled(true);
                            appletVal.SetAgentData(PV, 0, 2);
                        }

                    } else {
                        block();
                    }

                }
            }
        });

    }

    public class FindAgents extends OneShotBehaviour {

        public void action() {
            // find AIDs of neighboors
            try {
                //System.out.println("LoadAgent"+neighId[i]);
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setName("DSO agent");
                template.addServices(sd);
                DFAgentDescription[] result = DFService.search(myAgent, template);
                AgentAIDs[0] = result[0].getName();
                sd.setName("PV Agent");
                template.addServices(sd);
                result = DFService.search(myAgent, template);
                AgentAIDs[2] = result[0].getName();
                sd.setName("Load Agent");
                template.addServices(sd);
                result = DFService.search(myAgent, template);
                AgentAIDs[3] = result[0].getName();

            } catch (FIPAException ex) {
                Logger.getLogger(CurtailAgent.class.getName()).log(Level.SEVERE, null, ex);
            }

            //initializa GUI and add listeners
            appletVal = new SetCellValues();
            appletVal.playButton.addActionListener(new listener());
            appletVal.playButton.setEnabled(true);
            appletVal.resetButton.addActionListener(new listener());
            appletVal.resetButton.setEnabled(true);
            //operate microgrid
            addBehaviour(operate);

        }

    }

    public class waitAgents extends CyclicBehaviour {

        public void action() {

            if (AgentsUp == (NAgents - 1)) {
                System.out.println(getLocalName() + " says: all agents up waiting for next steps...");
                removeBehaviour(waitForAgents);
                AgentsUp = 0;
                addBehaviour(new FindAgents());

            }
            ACLMessage msg = receive();
            if (msg != null) {
                // Message received. Process it
                String val = msg.getContent().toString();
                if (val.contains("Hello")) {
                    AgentsUp++;
                }

            } else {
                block();
            }

        }

    }

    public class receiveFromAgents extends CyclicBehaviour {

        public void action() {

            String stripedValue = "";
            ACLMessage msg = receive();
            if (msg != null) {
                // Message received. Process it
                String val = msg.getContent().toString();
                stripedValue = (val.replaceAll("[\\s+a-zA-Z :]", ""));
                stripedValue = stripedValue.replace("=", "");
                //DSO case
                if (val.contains("DSO")) {
                    System.out.println(val);
                    DSO = Double.parseDouble(stripedValue);
                    AgentsUp++;
                } //PV
                else if (val.contains("PV")) {
                    System.out.println(val);
                    PV = Double.parseDouble(stripedValue);
                    AgentsUp++;
                } //Load
                else if (val.contains("load")) {
                    System.out.println(val);
                    Load = Double.parseDouble(stripedValue);
                    AgentsUp++;
                    if (val.contains("true")) {
                        loadFlexibility = true;
                    } else if (val.contains("false")) {
                        loadFlexibility = false;
                    }
                }

                if (AgentsUp == (NAgents - 1)) {
                    System.out.println(getLocalName() + " received from all agents...");
                    removeBehaviour(receiveAgents);
                    AgentsUp = 0;
                    refreshGui(DSO, SOC, PV, Load);
                    appletVal.playButton.setEnabled(true);

                    if (!initialization) {
                        //take Decision
                        addBehaviour(decision);
                    } else {
                        initialization = false;
                        initialLoad = Load;
                    }
                }

            } else {
                block();
            }

        }

    }

    public class opMicrogrid extends OneShotBehaviour {

        public void action() {
            addBehaviour(receiveAgents);
            //ask information from agents     
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID(AgentAIDs[0].getLocalName(), AID.ISLOCALNAME));
            msg.addReceiver(new AID(AgentAIDs[2].getLocalName(), AID.ISLOCALNAME));
            msg.addReceiver(new AID(AgentAIDs[3].getLocalName(), AID.ISLOCALNAME));
            msg.setContent("Inform!!!");
            send(msg);

        }
    }

    public class takeDecision extends OneShotBehaviour {

        public void action() {

            if (PV - DSO >= initialLoad) {
                // turn on load......
                if (Load == 0) {
                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(new AID(AgentAIDs[3].getLocalName(), AID.ISLOCALNAME));
                    msg.setContent("Change value to: " + initialLoad);
                    send(msg);
                    refreshGui(DSO, SOC, PV, initialLoad);
                } else {
                    System.out.println("No changes required....");
                }

            } else if (PV - DSO < initialLoad) {
                if (Load != 0) {
                    //turn load off......
                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(new AID(AgentAIDs[3].getLocalName(), AID.ISLOCALNAME));
                    msg.setContent("Change value to: " + 0.0);
                    send(msg);
                    refreshGui(DSO, SOC, PV, 0.0);
                } else {
                    System.out.println("No changes required....");
                }

            }

        }
    }

    public class listener implements ActionListener {

        public void actionPerformed(ActionEvent e) {

            if (e.getActionCommand().equals("PLaY")) {
                System.out.println("Start!!");

                addBehaviour(operate);
                appletVal.playButton.setEnabled(false);

            } else if (e.getActionCommand().equals("Reset")) {

                //set load and battery ......
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new AID(AgentAIDs[3].getLocalName(), AID.ISLOCALNAME));
                msg.setContent("Change value to: " + initialLoad);
                send(msg);
                Load = initialLoad;
                appletVal.SetAgentData(initialLoad, 0, 3);

            }
        }
    }

    public class updatePV extends OneShotBehaviour {

        public void action() {
            addBehaviour(receiveAgents);
            //ask information from agents     
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(new AID(AgentAIDs[0].getLocalName(), AID.ISLOCALNAME));
            msg.addReceiver(new AID(AgentAIDs[2].getLocalName(), AID.ISLOCALNAME));
            msg.addReceiver(new AID(AgentAIDs[3].getLocalName(), AID.ISLOCALNAME));
            msg.setContent("Inform!!!");
            send(msg);

        }
    }

    public void refreshGui(double dso, double soc, double pv, double load) {
        //send results to applet
        appletVal.SetAgentData(dso, 0, 0);
        appletVal.SetAgentData(soc, 0, 1);
        appletVal.SetAgentData(pv, 0, 2);
        appletVal.SetAgentData(load, 0, 3);
        appletVal.negotiationCompleted();

    }

    public void takeDown() {

        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        // Printout a dismissal message
        System.out.println(getAID().getName() + " terminating.");
        doDelete();

    }
}
