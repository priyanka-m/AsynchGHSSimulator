package AsynchGHSSimulator;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
* @author Shraddha Patel, Priyanka Menghani
*/
public class Node implements Runnable {
  int UID;
  String name;
  int level;
  int core;
  int mwoe;
  String status = "sleeping";
  Node parent = null;
  Node minChild;
  Map<Node, Edge> neighbors; // maps neighboring nodes to edges
  boolean terminated = false;
  BlockingQueue<Message> messageQueue = new ArrayBlockingQueue<Message>(1000, true);
  BlockingQueue<Message> deferredMsgs = new ArrayBlockingQueue<Message>(1000, true);
  List<Integer> receivedConnectFrom = new ArrayList<Integer>();
  List<Integer> sentConnectTo = new ArrayList<Integer>();
  List<Integer> receivedAcceptFrom = new ArrayList<Integer>();
  List<Integer> receivedRejectFrom = new ArrayList<Integer>();
  ArrayList<Edge> branchEdges = new ArrayList<Edge>();
  ArrayList<Edge> basicEdges = new ArrayList<Edge>();
  ArrayList<Edge> rejectedEdges = new ArrayList<Edge>();
  ArrayList<Edge> waitingForReport = new ArrayList<Edge>();
  ArrayList<Edge> branchEdgesWithoutMWOE = new ArrayList<Edge>();

  Node(int UID) {
    this.UID = UID;
    this.core = UID;
    this.name = "?";
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

  synchronized void sendMessage(Message m) {
    try {
      messageQueue.add(m);
      System.out.println("delivered " + m);
    } catch (Exception e) {
      closeConnection(e);

    }
  }
  /*
  *
  * */
  void sendEdges() {
    final TreeMap<Integer, Integer> h = new TreeMap<Integer, Integer>();
    Iterator it = neighbors.keySet().iterator();
    while (it.hasNext()) {
      Node n = (Node) it.next();
      System.out.println(this.UID + " has a neighbour with uid " + n.UID + " and edge weight is " + neighbors.get(n).getCost());
      h.put(((Edge) neighbors.get(n)).getCost(), n.UID);

    }
    (new Thread() {
      public void run() {
        sendMessage(new Message(UID, h));
      }
    }).start();
  }

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

  void receiveMessages() {
    try {
      while (true) {
        Message m = (Message) messageQueue.poll();
        System.out.println("message of type " + m.messageType + " received");
        if (m != null) {
          switch (m.messageType) {
            case Message.REGISTRATION:
              name = (String) m.data;
              break;
            case Message.WAKEUP:
              Initiate(UID, core, level);
//              Node minWeightNeighbour = findMinWeightNeighbour();
//              if (!sentConnectTo.contains(minWeightNeighbour.UID)) {
//                System.out.println("sent connect to of " + UID + " contains " + minWeightNeighbour.UID + " sending connect from " + UID + " to " + minWeightNeighbour + " core " + core + " level " + level);
//                neighbors.get(minWeightNeighbour).forwardMessage(this, minWeightNeighbour, new Message(Message.CONNECT, UID, minWeightNeighbour.UID, core, level));
//              }
              break;
            case Message.INITIATE:
              Initiate(m.sender, m.core, m.level);
              break;
            case Message.TEST:
              System.out.print("Test received");
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
              System.out.println("connect received " + m.sender + m.destination);
              if (m.sender != UID && m.destination == UID) {
                MSTviewer.nodes.get(m.sender).sentConnectTo.add(UID);
                receivedConnectFrom.add(m.sender);
                if (sentConnectTo.contains(m.sender) && receivedConnectFrom.contains(m.sender)) {
                  connect(m.sender, m.destination, m.core, m.level);
                } else {
                  System.out.println("adding msg from " + m.sender + " to " + m.destination + " to deffered msgs ");
                  deferredMsgs.add(m);
                }
              } else {
                forwardMessage(m);
              }
              break;
            case Message.INFORM:
              level = m.level;
              core = m.core;
              // don't break
              Inform(m.sender, m.core, m.level);
            default:
              forwardMessage(m);
          }
        }
      }
    } catch (Exception e) {
      closeConnection(e);
    }
  }

  Node findNodeIncidentOnEdge(Edge incidentEdge) {
    for (Node n : neighbors.keySet()) {
      if (neighbors.get(n).equals(incidentEdge)) {
        System.out.println("node found with id " + n.UID);
        return n;
      }
    }
    return null;
  }

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

  void Initiate(int sender, int core, int level) {
    status = "SEARCHING";
    this.core = core;
    this.level = level;
    processDeferredTests();
    this.parent = (Node) MSTviewer.nodes.get(sender);
    this.minChild = null;


    waitingForReport = branchEdges;
    waitingForReport.remove(neighbors.get(MSTviewer.nodes.get(sender)));
    //let waitingForReport contain all branch edges (besides E, if sender != self)
    for (Edge e : branchEdges) {
      Node neighbour = findNodeIncidentOnEdge(e);
      if (neighbour != null && neighbour != MSTviewer.nodes.get(sender)) {
        System.out.println(UID + " sending initiate to " + neighbour.UID);
        e.forwardMessage(this, neighbour, new Message(Message.INITIATE, this.UID, neighbour.UID, this.core, this.level));
      }
    }
    //send "Initiate(core,level)" over all branch edges (besides E, sender != self)
    //MWOE.cost =
    mwoe = 100000;
    Edge lowestCostBasicEdge = findLowestCostBasicEdge();
    while (!basicEdges.isEmpty() && (mwoe == 100000  || mwoe > lowestCostBasicEdge.cost)) {
      Node neighbour = findNodeIncidentOnEdge(lowestCostBasicEdge);
      System.out.println(UID + " My lowest cost basic edge is to " + neighbour.UID);
      lowestCostBasicEdge.forwardMessage(this, neighbour, new Message(Message.TEST, this.UID, neighbour.UID, this.core, this.level));
      //wait for a response for the test message.
      while (!receivedAcceptFrom.contains(neighbour.UID) || !receivedRejectFrom.contains(neighbour.UID)) {}
    }

    try {
      while (true) {
        if (waitingForReport.isEmpty()) {
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
          break;
        }
      }
    } catch (Exception e) {
      closeConnection(e);
    }
  }

  void Test(Message m) {
    System.out.println("Test called for " + UID);
    if (this.core == m.core) {// in the same fragment
      System.out.println(" in the same fragment ");
      neighbors.get(MSTviewer.nodes.get(m.sender)).forwardMessage(this, MSTviewer.nodes.get(m.sender), new Message(Message.REJECT, UID, m.sender));
      rejectedEdges.add(neighbors.get(MSTviewer.nodes.get(m.sender)));
    } else if (this.level >= m.level) {// can't be in the same fragment
      neighbors.get(MSTviewer.nodes.get(m.sender)).forwardMessage(this, MSTviewer.nodes.get(m.sender), new Message(Message.ACCEPT, UID, m.sender));
      System.out.println(" not in the same fragment ");
    } else { // don't know yet because we haven't reached that level
        System.out.print("Adding to deferred messages");
        deferredMsgs.add(m);
      }
  }

  void Accept(int sender) {
    if (neighbors.get(MSTviewer.nodes.get(sender)).getCost() < mwoe) {
      System.out.println("In accept()");
      minChild = null;
      mwoe = neighbors.get(MSTviewer.nodes.get(sender)).getCost();
    }
  }

  void Reject(int sender) {
      System.out.println("In reject()");
    receivedRejectFrom.add(sender);
    rejectedEdges.add(neighbors.get(MSTviewer.nodes.get(sender)));
    basicEdges.remove(neighbors.get(MSTviewer.nodes.get(sender)));
  }

  void Report(int sender, int cost) {
    waitingForReport.remove(neighbors.get(MSTviewer.nodes.get(sender)));
    if (cost < mwoe) {
      minChild = MSTviewer.nodes.get(sender);
      mwoe = cost;
    }
  }

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

  void connect(int src, int dest, int core, int level) {
    System.out.println("connect called");
    branchEdges.add(neighbors.get(MSTviewer.nodes.get(src)));
    basicEdges.remove(neighbors.get(MSTviewer.nodes.get(src)));
    if (this.level > level) {// *** ABSORB THE OTHER FRAGMENT ***
      System.out.println("checking leve diff btwn " + src + " and " + dest);
      if (status.equals("FOUND"))  // MWOE can't be in the absorbed fragment
        neighbors.get(MSTviewer.nodes.get(src)).forwardMessage(this, MSTviewer.nodes.get(src), new Message(Message.INFORM, dest, src, core, level));
      if (status.equals("SEARCHING")) {// MWOE might be in the absorbed fragment
        waitingForReport.add(neighbors.get(MSTviewer.nodes.get(src)));
        neighbors.get(MSTviewer.nodes.get(src)).forwardMessage(this, MSTviewer.nodes.get(src), new Message(Message.INITIATE, UID, MSTviewer.nodes.get(src).UID, core, level));
      }
    } else {// levels are the same, so *** MERGE WITH THE OTHER FRAGMENT ***
      this.core = Math.max(src, dest);
      System.out.println("core is " + this.core + " increasing level of " + UID);
      this.level++;
      processDeferredTests();
      if (this.core == this.UID) {// WE'RE THE NEW ROOT, SO START THE MWOE SEARCH
        System.out.println("new leader is " + this.core);
        sendMessage(new Message(Message.INITIATE, this.UID, this.UID, this.core, this.level));// start broadcast
      }
    }
  }

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
    //   send "Inform(core,level)" over all other branch edges (besides E)
  }

  void allDone(int sender) {
    for (Edge e : branchEdges) {
      Node neighbour = findNodeIncidentOnEdge(e);
      if (neighbour != null && neighbour != MSTviewer.nodes.get(sender))
          e.forwardMessage(this, neighbour, new Message(Message.ALL_DONE, UID, neighbour.UID));
    }
    closeConnection("Thread " + UID + " Terminated");
  }

  void processDeferredTests() {
    for (Message m :  deferredMsgs) {
      if (this.core == m.core) {
        System.out.println("rejecting msg from " + m.sender + " to " + m.destination);
        MSTviewer.nodes.get(m.sender).sendMessage(new Message(Message.REJECT, UID, m.sender));
        deferredMsgs.remove(m);
      } else if (this.level >= m.level) {
        System.out.println("accepting msg from " + m.sender + " to " + m.destination);
        MSTviewer.nodes.get(m.sender).sendMessage(new Message(Message.ACCEPT, UID, m.sender));
        deferredMsgs.remove(m);
      }
    }
  }

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
      System.out.println("Closing socket to " + name + " due to: " + reason);
      if (reason instanceof Exception)
        ((Exception) reason).printStackTrace();
    }
    terminated = true;
  }
}

