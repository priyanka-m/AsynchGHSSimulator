package AynchGHSSimulator;
/**
 * MSTviewer.java
 *
 * Kenneth J. Goldman
 * Washington University
 * September, 2002
 *
 * This is a server to coordinate testing/execution of nodes in a
 * minimum spanning tree computation.  It allows nodes to connect,
 * constructs the graph, and provides a visualization as the
 * computation unfolds.  In detail...
 *
 * What this viewer does:
 * 1. Opens ServerSocket at PORT_NUM;
 * 2. Accepts "Registration" messages, adding nodes to the visualization.
 *      Each node is assigned a unique integer identifier (UID).
 * 3. Expects user to click "start" button when the nodes have conencted.
 *      Nodes that connect afterward "start" is pressed may not be
 *      added to the graph.
 * 4. Creates edges at random to form a connected graph.  (Euclidian
 *      distance is used as the edge cost for natural visualization.)
 * 5. Sends back a "Registration" message to each node with an edge list.
 * 6. Sends "Wakeup" messages to nodes the user clicks on.
 * 7. Forwards messages sent along edges between nodes, monitoring those
 *      messsages to update the visualization.
 * 8. Resets when user clicks "terminate" button, so a new session can start
 *      without restarting the server.
 *
 * What the visualization shows:
 * 1. Each node with its UID, followed by its current (core,level) pair
 *    as can be determined by the messages sent and received so far.
 *    The node name is shown as mouse-over text.
 *    (Greyed-out nodes have disconnected from the server.)
 * 2. A "start" button (see item (3) above).
 * 3. Each graph edge: connecting (white), basic (blue),
 *                     rejected (gray), and branch (green).
 * 4. Messages in transit.
 * 5. A "pause" button for freezing the global execution.
 * 6. A slider to control the message latency.
 * 6. In addition, each message delivered is printed to the standard output.
 *
 * What this server expects from its clients:
 * 1. All messages are instances of the accompanying Message class.
 * 2. Upon connecting, the client sends a REGISTRATION Message with
 *      a String as the serverData (to be used as the node name).
 * 3. The server (eventually) replies with a REGISTRATION Message with
 *      a collection of edges as the serverData.  The collection is
 *      a TreeMap from Integer objects (edge costs) to Integer objects (UIDs).
 *      NOTE: The mapping is from COSTS TO UIDs (not the other way around).
 * 4. The server then expects a series of Message objects from the client
 *      to be forwarded to a destination (the destination UID must be one
 *      of the nodes in the clients edge collection).
 */

import java.io.FileReader;
import java.io.StreamTokenizer;
import java.net.*;
import java.util.*;

public class MSTviewer implements Runnable {
  //data:
  static final int MAX_NODES = 50; // limit on the number of nodes in graph
  boolean started; // true when the algorithm has been started up by user
  ArrayList<Node> nodes = new ArrayList<Node>();   // Node objects indexed by unique IDs
  static int edgeCount = 0, nodeCount = 0;
  static int[][] connections;

  synchronized void setStarted(boolean b) {started = b;}
  synchronized boolean isStarted() {return started;}

  public MSTviewer() {
    setStarted(true);
  }

  public void run() {
    while (true) {
      try {
        acceptRegistration();
        createEdges();
   //     sendEdges();
//        waitForTermination();
      } catch (Exception e) {
        System.out.println(" stopped "+ e);
        e.printStackTrace();
      }
    }
  }

  void closeConnections() {
    Iterator it = nodes.iterator();
    while (it.hasNext()) {
      Node n = (Node) it.next();
      if (n != null) {
        n.closeConnection("normal termination");
      }
    }
  }

  void acceptRegistration() {
      for (int i = 0; i < nodeCount; i++) {
        if ((nodes.size() < MAX_NODES+1 || nodes.size() <= 3) && !isStarted()) {
          try {
            nodes.add(new Node(nodes.size()));
          } catch (Exception e) {System.out.println(e);}
        }
      }

  }

  void createEdges() {
    // first, create a cycle through the graph to ensure connectedness
    for (int i = 0; i < nodeCount; i++) {
      for (int j = 0; j < nodeCount; j++) {
        if (connections[i][j] == 1) {
          new Edge(nodes.get(i), nodes.get(j));
          // To avoid creation of another edge from j to i. We can alter the array connections because
          // we don't need it anymore.
          connections[i][j] = 0;
          connections[j][i] = 0;
        }
      }

    }
  }

  // Sends a copy of all edges to all nodes.
  void sendEdges() {
    Iterator it = nodes.iterator();
    while (it.hasNext()) {
      Node node = (Node) it.next();
      if (node != null) // skip dummy entry 0
        node.sendEdges();
    }
  }

  /*void waitForTermination() {
    while (isStarted())
      synchronized(endButton) {
        try {endButton.wait();} catch (InterruptedException ie) {}
      }
    System.out.println("\n SESSION TERMINATED BY USER \n");
  }*/

  public static void main(String[] args) {
    int[] arrayIds = null;

    try{
      StreamTokenizer tokenizer = new StreamTokenizer(new FileReader("Inputs.txt"));
      tokenizer.slashSlashComments(true);
      tokenizer.eolIsSignificant(false);
      tokenizer.nextToken();
      nodeCount = (int)tokenizer.nval;
      System.out.println("Node Count::"+nodeCount);
      arrayIds = new int[nodeCount];
      connections = new int[nodeCount][nodeCount];

      for(int i=0;i<nodeCount;i++){
        tokenizer.nextToken();
        if (tokenizer.ttype == StreamTokenizer.TT_NUMBER){
          arrayIds[i] = (int)tokenizer.nval;
          System.out.println("Array ID "+(i+1)+"::"+arrayIds[i]);
        }
      }
      for(int i=0;i<nodeCount;i++){
        for(int j=0;j<nodeCount;j++){
          tokenizer.nextToken();
          if (tokenizer.ttype == StreamTokenizer.TT_NUMBER){
            connections[i][j] = (int)tokenizer.nval;
          }
        }
      }
    }catch(Exception e){
      System.out.println("Exception::" +e);
    }
    MSTviewer viewer = new MSTviewer();
    (new Thread(viewer)).start();
  }
}
