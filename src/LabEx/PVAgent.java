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
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import java.util.logging.Level;
import java.util.logging.Logger;


public class PVAgent extends Agent {

    final int NAgents = 4;
    private AID[] AgentAIDs = new AID[NAgents];
    Behaviour listen;
    final double PVproduction = 1000; //W will be read from the DB
    //Database access
    //DBConnect dbc = new DBConnect();
    
    protected void setup() {
        
        //Register Agent on the DF
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setName("PV Agent");
        sd.setType("PV info");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        listen = new listenState();
        
        //connect with DB
        //            try {
//
//                dbc.Connect();
//                //Result set get the result of the SQL query
//                dbc.statement = dbc.connect.createStatement();
//                
//            } catch (Exception ex) {
//                Logger.getLogger(Coordinator.class.getName()).log(Level.SEVERE, null, ex);
//            }
        
        
         /* discover neighboors and coordinator */
        addBehaviour(new WakerBehaviour(this, 3000) {
            protected void handleElapsedTimeout() {
                addBehaviour(new FindAgents());
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
                    sd.setName("Storage Agent");
                    template.addServices(sd);
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    AgentAIDs[1] = result[0].getName();
                    sd.setName("DSO agent");
                    template.addServices(sd);
                    result = DFService.search(myAgent, template);
                    AgentAIDs[0] = result[0].getName();
                    sd.setName("Load Agent");
                    template.addServices(sd);
                    result = DFService.search(myAgent, template);
                    AgentAIDs[3] = result[0].getName();
                    

                } catch (FIPAException ex) {
                    Logger.getLogger(CurtailAgent.class.getName()).log(Level.SEVERE, null, ex);
                }
                //inform and wait...
                addBehaviour(new informCoordinator());
            }

        }
     public class informCoordinator extends OneShotBehaviour {
        public void action() {
            //send message to coordinator
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(AgentAIDs[1]);
            msg.setContent("Hello from " + getLocalName() + ", waiting...!");
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
                if (val.contains("Inform")) {
                    removeBehaviour(listen);
                    addBehaviour(new sendValues());
                }
            } else {
                block();
            }
        }
    }
     
      public class sendValues extends OneShotBehaviour {
        public void action() {
//            //read PV production from DB
//            dbc.resultSet = dbc.statement
//                        .executeQuery("select LoadStatus from agents");
//                
//                //System.out.println("Showing: " + dbc.resultSet.getMetaData().getColumnName(1) + " results.....");
//                int i;
//                while (dbc.resultSet.next()) {
//                    int LoadStatus = dbc.resultSet.getInt("LoadStatus");
//                }  
            
            //send message to coordinator
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(AgentAIDs[1]);
            msg.setContent(getLocalName() + " produces: " + PVproduction + "W");
            send(msg);

            //go to listen behaviour
            addBehaviour(listen);

        }
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
