package AsynchGHSSimulator;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
* @author Shraddha Patel, Priyanka Menghani
*/
public class Node implements Runnable {
  int UID;   //UID of the node
  int level;    //level of the component
  int core;     //UID of core
  int mwoe;     //weight of mwoe of particular component
  String status = "sleeping";   //status of the node
  Node parent = null;       //parent node
  Node minChild;        //child node of the root
  Map<Node, Edge> neighbors; // maps neighboring nodes to edges

  boolean terminated = false;
  BlockingQueue<Message> messageQueue = new ArrayBlockingQueue<Message>(1000, true); //queue to store each incoming message
  BlockingQueue<Message> deferredMsgs = new ArrayBlockingQueue<Message>(1000, true);  //queue to store deferred messages
  List<Integer> receivedConnectFrom = new ArrayList<Integer>();//list of nodes from which connect request has been received
  List<Integer> sentConnectTo = new ArrayList<Integer>();//list of nodes to which connect request has been sent
  List<Integer> receivedAcceptFrom = new ArrayList<Integer>();//list of nodes from which accept request has been received
  List<Integer> receivedRejectFrom = new ArrayList<Integer>();//list of nodes from which reject request has been received
  ArrayList<Edge> branchEdges = new ArrayList<Edge>();//list of branchedges of MST
  ArrayList<Edge> basicEdges = new ArrayList<Edge>();//list of edges that are not part of MST
  ArrayList<Edge> rejectedEdges = new ArrayList<Edge>();//list of rejected edges
  ArrayList<Edge> waitingForReport = new ArrayList<Edge>();//list of nodes from which report request is to be received
  ArrayList<Edge> branchEdgesWithoutMWOE = new ArrayList<Edge>();//list of branch edges without minimum weight outgoing edge

  Node(int UID) {
    this.UID = UID;
    this.core = UID;
    this.level = 0;
    neighbors = new HashMap<Node, Edge>();
  }

  Map getNeighbors() {
    return neighbors;
  }

  public void run() {
    try {
      receiveMessages();
    } catch (Exception e) {
      closeConnection(e);
    }
  }

  /**
   * Method to store a message in a queue (Ensures ordered message delivery)
   * @param m
   */
  synchronized void sendMessage(Message m) {
    try {
      messageQueue.add(m);
      System.out.println("delivered " + m);
      if (m.messageType == 4) {
        synchronized (receivedAcceptFrom) {
          System.out.println("accept and notify");
          receivedAcceptFrom.add(m.sender);
          notifyAll();
        }
      } else if (m.messageType == 5) {
        synchronized (receivedRejectFrom) {
          receivedRejectFrom.add(m.sender);
          notify();
        }
      }
    } catch (Exception e) {
      closeConnection(e);

    }
  }

  /**
   * Method to find a neighbor node having least edge weight leading to it
   * @return Node
   */
  Node findMinWeightNeighbour() {
    int minCost = 10000;
    Node minWeightNeighbour = null;
    for (Node n : neighbors.keySet()) {
      if (minCost > neighbors.get(n).getCost()) {
        minCost = neighbors.get(n).getCost();
        minWeightNeighbour = n;
      }
    }
    return minWeightNeighbour;
  }

  /**
   * Method that performs action according to message type
   */
  void receiveMessages() {
    try {
      while (true) {
        Message m = (Message) messageQueue.poll();
        if (m != null) {
          switch (m.messageType) {
            case Message.WAKEUP:
              Initiate(UID, core, level);
              break;
            case Message.INITIATE:
              Initiate(m.sender, m.core, m.level);
              break;
            case Message.TEST:
              Test(m);
              break;
            case Message.ACCEPT:
              Accept(m.sender);
              break;
            case Message.REJECT:
              Reject(m.sender);
              break;
            case Message.REPORT:
              Report(m.sender, m.cost);
              break;
            case Message.CHANGE_ROOT:
              changeRoot();
              break;
            case Message.CONNECT:
              if (m.sender != UID && m.destination == UID) {
                MSTviewer.nodes.get(m.sender).sentConnectTo.add(UID);
                receivedConnectFrom.add(m.sender);
                if (sentConnectTo.contains(m.sender) && receivedConnectFrom.contains(m.sender)) {
                  connect(m.sender, m.destination, m.core, m.level);
                } else {
                  System.out.println("Adding to deferred messages");
                  deferredMsgs.add(m);
                }
              } else {
                forwardMessage(m);
              }
              break;
            case Message.INFORM:
              level = m.level;
              core = m.core;
              Inform(m.sender, m.core, m.level);
            case Message.ALL_DONE:
              allDone(m.sender);
              break;
            default:
              forwardMessage(m);
          }
        }
      }
    } catch (Exception e) {
      closeConnection(e);
    }
  }

  /**
   * Method to find an incident node
   * @param incidentEdge
   * @return node
   */
  Node findNodeIncidentOnEdge(Edge incidentEdge) {
    for (Node n : neighbors.keySet()) {
      if (neighbors.get(n).equals(incidentEdge)) {
        return n;
      }
    }
    return null;
  }

  /**
   * method to find basic edge having lowest edge weight
   * @return edge
   */
  Edge findLowestCostBasicEdge() {
    int minCost = 100000;
    Edge minCostEdge = null;
    for (Edge e : basicEdges) {
      if (e.cost < minCost) {
        minCost = e.cost;
        minCostEdge = e;
      }
    }
    return minCostEdge;
  }

 /** Method to broadcast over a fragment from the core to
  notify them of the core and level of the fragment AND to
  tell nodes to search for the MWOE of the fragment
  * @param sender UID of the node that sent the message
  * @param core core of the component
  * @param level level of the component
  */
  void Initiate(int sender, final int core, final int level) {
    status = "SEARCHING";
    this.core = core;
    this.level = level;
    processDeferredTests();
    this.parent = (Node) MSTviewer.nodes.get(sender);
    this.minChild = null;


    waitingForReport = branchEdges;
    waitingForReport.remove(neighbors.get(MSTviewer.nodes.get(sender)));

    for (Edge e : branchEdges) {
      Node neighbour = findNodeIncidentOnEdge(e);
      if (neighbour != null && neighbour != MSTviewer.nodes.get(sender)) {
        e.forwardMessage(this, neighbour, new Message(Message.INITIATE, this.UID, neighbour.UID, this.core, this.level));
      }
    }

    mwoe = 100000;
    final Edge lowestCostBasicEdge = findLowestCostBasicEdge();
    final Node thisNode = this;
    if (!basicEdges.isEmpty() && (mwoe == 100000  || mwoe > lowestCostBasicEdge.cost)) {
      final Node neighbour = findNodeIncidentOnEdge(lowestCostBasicEdge);
      lowestCostBasicEdge.forwardMessage(thisNode, neighbour, new Message(Message.TEST, UID, neighbour.UID, core, level));
    }
//    (new Thread() {
//      public void run() {
//
//          //wait for a response for the test message.
//          //synchronized (this) {
//            while (!receivedAcceptFrom.contains(neighbour.UID) || !receivedRejectFrom.contains(neighbour.UID)) {
//              //try{
//                //wait();
//              //} catch (Exception e) {
//                //e.printStackTrace();
//              //}
//            }
//          //}
//          System.out.println("notified");
//          if (receivedAcceptFrom.contains(neighbour.UID)) {
//            System.out.println("Received accept from " + neighbour.UID);
//            break;
//          } else if (receivedRejectFrom.contains(neighbour.UID)) {
//            continue;
//          }
//        }
//        try {
//          if (waitingForReport == null || waitingForReport.isEmpty()) {
//            System.out.println("waiting for report is now empty");
//            if (parent != thisNode) {
//              status = "FOUND";
//              parent.sendMessage(new Message(Message.REPORT, UID, parent.UID, mwoe));// this will be the minimum in subtree
//            } else if (mwoe != 100000) {
//              sendMessage(new Message(Message.CHANGE_ROOT, UID, UID));
//            } else {
//              for (Edge branchEdge : branchEdges) {
//                Node n = findNodeIncidentOnEdge(branchEdge);
//                branchEdge.forwardMessage(thisNode, n, new Message(Message.ALL_DONE, UID, n.UID));
//              }
//              closeConnection("Thread " + UID + " Terminated");
//            }
//          }
//
//        } catch (Exception e) {
//          closeConnection(e);
//        }
//      }
//    }).start();

  }

  /**
   * Method to send test request across an edge to determine if that edge
   *     leads to another fragment
   * @param m
   */
  void Test(Message m) {
    if (this.core == m.core) {// in the same fragment
      neighbors.get(MSTviewer.nodes.get(m.sender)).forwardMessage(this, MSTviewer.nodes.get(m.sender), new Message(Message.REJECT, UID, m.sender));
      rejectedEdges.add(neighbors.get(MSTviewer.nodes.get(m.sender)));
    } else if (this.level >= m.level) {// can't be in the same fragment
      neighbors.get(MSTviewer.nodes.get(m.sender)).forwardMessage(this, MSTviewer.nodes.get(m.sender), new Message(Message.ACCEPT, UID, m.sender));
    } else { // don't know yet because we haven't reached that level
        deferredMsgs.add(m);
      }
  }

  void checkStatus() {
    if (waitingForReport == null || waitingForReport.isEmpty()) {
      System.out.println("waiting for report is now empty");
      if (parent != this) {
        status = "FOUND";
        parent.sendMessage(new Message(Message.REPORT, UID, parent.UID, mwoe));// this will be the minimum in subtree
      } else if (mwoe != 100000) {
        sendMessage(new Message(Message.CHANGE_ROOT, UID, UID));
      } else {
        for (Edge branchEdge : branchEdges) {
          Node n = findNodeIncidentOnEdge(branchEdge);
          branchEdge.forwardMessage(this, n, new Message(Message.ALL_DONE, UID, n.UID));
        }
        closeConnection("Thread " + UID + " Terminated");
      }
    }
  }
  /**
   * Method to give response to a Test message indicating a different fragment
   * @param sender
   */
  void Accept(int sender) {
    if (neighbors.get(MSTviewer.nodes.get(sender)).getCost() < mwoe) {
      minChild = null;
      mwoe = neighbors.get(MSTviewer.nodes.get(sender)).getCost();
      checkStatus();
    }
  }

  void resendTest() {
    final Edge lowestCostBasicEdge = findLowestCostBasicEdge();
    final Node thisNode = this;
    if (!basicEdges.isEmpty() && (mwoe == 100000  || mwoe > lowestCostBasicEdge.cost)) {
      final Node neighbour = findNodeIncidentOnEdge(lowestCostBasicEdge);
      lowestCostBasicEdge.forwardMessage(thisNode, neighbour, new Message(Message.TEST, UID, neighbour.UID, core, level));
    }
  }
  /**
   * Method to give response to a Test message indicating the same fragment
   * @param sender UID of the node that sent the message
   */
  void Reject(int sender) {
    rejectedEdges.add(neighbors.get(MSTviewer.nodes.get(sender)));
    basicEdges.remove(neighbors.get(MSTviewer.nodes.get(sender)));
    resendTest();
  }

  /**
   * Method to inform parent of MWOE of subtree
   * @param sender UID of the node that sent the message
   * @param cost cost of the MWOE
   */
  void Report(int sender, int cost) {
    waitingForReport.remove(neighbors.get(MSTviewer.nodes.get(sender)));
    if (cost < mwoe) {
      minChild = MSTviewer.nodes.get(sender);
      mwoe = cost;
    }
    checkStatus();
  }


  /**
   * Method to send message from core to MWOE of subtree, in order
   *  to tell that incident node to connect along that MWOE
   */
  void changeRoot() {
    status = "FOUND";
    if (minChild != null)
      neighbors.get(minChild).forwardMessage(this, minChild, new Message(Message.CHANGE_ROOT, this.UID, minChild.UID));
    else{
      Edge e = findLowestCostBasicEdge();
      Node n = findNodeIncidentOnEdge(e);
      branchEdges.add(e);
      e.forwardMessage(this, n, new Message(Message.CONNECT, UID, n.UID, core, level));
    }
  }

  /**
   * Method to send message over an edge in order to combine
   *  the fragments of the incident nodes of that edge
   * @param src UID of the node sending the connect
   * @param dest UID of the node receiving the connect
   * @param core core of the component
   * @param level level of the component
   */
  void connect(int src, int dest, int core, int level) {
    branchEdges.add(neighbors.get(MSTviewer.nodes.get(src)));
    basicEdges.remove(neighbors.get(MSTviewer.nodes.get(src)));

    if (this.level > level) {// *** ABSORB THE OTHER FRAGMENT ***
      if (status.equals("FOUND"))  // MWOE can't be in the absorbed fragment
        neighbors.get(MSTviewer.nodes.get(src)).forwardMessage(this, MSTviewer.nodes.get(src), new Message(Message.INFORM, dest, src, core, level));

      if (status.equals("SEARCHING")) {// MWOE might be in the absorbed fragment
        waitingForReport.add(neighbors.get(MSTviewer.nodes.get(src)));
        neighbors.get(MSTviewer.nodes.get(src)).forwardMessage(this, MSTviewer.nodes.get(src), new Message(Message.INITIATE, UID, MSTviewer.nodes.get(src).UID, core, level));
      }
    } else {// levels are the same, so *** MERGE WITH THE OTHER FRAGMENT ***
      this.core = Math.max(src, dest);
      this.level++;
      processDeferredTests();
      if (this.core == this.UID) {// WE'RE THE NEW ROOT, SO START THE MWOE SEARCH
        sendMessage(new Message(Message.INITIATE, this.UID, this.UID, this.core, this.level));// start broadcast
      }
    }
  }

  /**
   * Method to broadcast over a fragment from the core to
   *  notify them of the core and level of the fragment
   * @param sender UID of the node that sent the message
   * @param core core of the component
   * @param level level of the component
   */
  void Inform(int sender, int core,int level){
    this.core = core;
    this.level = level;
    processDeferredTests();
    for (Edge e : branchEdges) {
      Node neighbour = findNodeIncidentOnEdge(e);
      if (neighbour != null && neighbour != MSTviewer.nodes.get(sender)) {
        e.forwardMessage(this, neighbour, new Message(Message.INFORM, this.UID, neighbour.UID, this.core, this.level));
      }
    }
  }

  /**
   * Method that broadcasts over the tree to inform nodes of termination
   * @param sender: UID of the node that sent the message
   */
  void allDone(int sender) {
    for (Edge e : branchEdges) {
      Node neighbour = findNodeIncidentOnEdge(e);
      if (neighbour != null && neighbour != MSTviewer.nodes.get(sender))
          e.forwardMessage(this, neighbour, new Message(Message.ALL_DONE, UID, neighbour.UID));
    }
    closeConnection("Thread " + UID + " Terminated");
  }

  /**
   * Method to process deferred test messages
   */
  void processDeferredTests() {
    for (Message m :  deferredMsgs) {
      if (this.core == m.core) {
        MSTviewer.nodes.get(m.sender).sendMessage(new Message(Message.REJECT, UID, m.sender));
        deferredMsgs.remove(m);
      } else if (this.level >= m.level) {
        MSTviewer.nodes.get(m.sender).sendMessage(new Message(Message.ACCEPT, UID, m.sender));
        deferredMsgs.remove(m);
      }
    }
  }
  /**
   * Method to forward message along and edge
   * @param m: Message to be forwarded
   */
  void forwardMessage(Message m) {
    if (m.destination == UID)
      sendMessage(m);
    else {
      Node n = (Node) MSTviewer.nodes.get(m.destination);
      Edge e = (Edge) neighbors.get(n);
      e.forwardMessage(this, n, m);
    }
  }

  void closeConnection(Object reason) {
    if (!terminated) { // print only if not terminated yet
      System.out.println("Closing thread " + UID + " due to: " + reason);
      if (reason instanceof Exception)
        ((Exception) reason).printStackTrace();
    }
    terminated = true;
  }
}

