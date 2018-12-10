package LabEx;

import jade.core.Runtime;
import jade.core.Profile;
import jade.core.ProfileImpl;

import jade.wrapper.*;

public class MainPlatform {

    private static Runtime rt;
    private static ProfileImpl pContainer;
    //private static MyApplet app;

    public MainPlatform() {
    }

    @SuppressWarnings("empty-statement")
    public static void main(String args[]) throws StaleProxyException {
            
        
        try {

           // Get a hold on JADE runtime
            rt = Runtime.instance();

            // Exit the JVM when there are no more containers around
            rt.setCloseVM(true);

            // Launch a complete platform on the 1099 port
            // create a default Profile
            Profile pMain = new ProfileImpl("localhost", 1099, "MyPlatform");

            System.out.println("Launching a whole in-process platform..." + pMain);
            AgentContainer mc = rt.createMainContainer(pMain);
            rt.setCloseVM(true);

            // set now the default Profile to start a container
            pContainer = new ProfileImpl("localhost", 1099, "Main-Container");
            System.out.println("Launching the agent container ..." + pContainer);

            //Starting Main container
            System.out.println("Launching the rma agent on the main container ...");
            AgentController rma = mc.createNewAgent("rma", "jade.tools.rma.rma", new Object[0]);
            rma.start();

            AgentContainer cont = rt.createAgentContainer(pContainer);
            System.out.println("Launching the agent container after ..." + pContainer);
            
            //Start the DSO agent
            System.out.println("Launching the DSO agent ...");
            AgentController coordinator = cont.createNewAgent("DSOAgent", "LabEx.DSOAgent", new Object[0]);
            coordinator.start();
            
            //Start PV agent
            System.out.println("Launching the PV agent agent ...");
            AgentController pvAgent = cont.createNewAgent("PVAgent", "LabEx.PVAgent", new Object[0]);
            pvAgent.start();
            
            //Start Storage agent
            System.out.println("Launching the Storage agent agent ...");
            AgentController storageAgent = cont.createNewAgent("storageAgent", "LabEx.StorageAgent", new Object[0]);
            storageAgent.start();
            
            //Start Load agent
            System.out.println("Launching the Load agent agent ...");
            AgentController loadAgent = cont.createNewAgent("loadAgent", "LabEx.LoadAgent", new Object[0]);
            loadAgent.start();
            
           
            } catch (Exception e) {
        }
        
    }
    
    
}
            

