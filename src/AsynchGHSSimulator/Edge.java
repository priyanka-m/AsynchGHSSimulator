package AsynchGHSSimulator;

import java.util.LinkedList;
import java.util.Random;

/**
 * Created on 11/25/14.
 */
public class Edge {
  Node a, b;
  int edgeID;
  int cost;
  int inTransit;
  boolean isBranch;
  String type;
  LinkedList aQueue, bQueue;
  boolean isRejected;
  
  Edge(Node a, Node b) {
    edgeID = MSTviewer.edgeCount++;
    this.a = a;
    this.b = b;
    this.cost = a.UID + b.UID;
    a.getNeighbors().put(b,this);
    b.getNeighbors().put(a,this);
    a.basicEdges.add(this);
    b.basicEdges.add(this);
    aQueue = new LinkedList(); // ensures FIFO delivery from a to b
    bQueue = new LinkedList(); // ensures FIFO delivery from b to a
    System.out.println(" Edge between " + a.UID + " and " + b.UID);
    isBranch = false;
    isRejected = false;
  }

  int getCost() { // use Euclidian distance, with unique low order bits
    return this.a.UID + this.b.UID;
  }

  // Messages sent over an edge are forwarded to their destinations
  void forwardMessage(final Node src,
                      final Node dest,
                      final Message m) {
    if (!((src == a && dest == b) ||
        (src == b && dest == a)))
      throw new IllegalArgumentException("message " + m +
          " can't travel along " + this);
    final LinkedList messageQueue;
    if (src == a) {
      messageQueue = aQueue;
    } else {
      messageQueue = bQueue;
    }
    final int priorCount = messageQueue.size();

    synchronized (messageQueue) {
      messageQueue.addLast(m); // enqueue
    }
    switch (m.messageType) {
      case Message.REJECT:
        if (!isBranch)
          break;
      case Message.CONNECT:
        this.isBranch = true;
        a.basicEdges.remove(this);
        a.branchEdges.add(this);
        b.basicEdges.remove(this);
        b.branchEdges.add(this);
        break;
      case Message.INITIATE:
      case Message.INFORM:
        isBranch = true;
        dest.level = m.level;
        dest.core = m.core;
    }
    //(new Thread() {
    //public void run() { // animation from src to dest, then delivery
//        if (getDelay() > 0) {
//          try {
//            Thread.sleep(getDelay()*20*priorCount);
//          } catch (InterruptedException ie) {}
//        }
    Message toDeliver;
        synchronized (messageQueue) {
          toDeliver = (Message)  messageQueue.removeFirst(); //dequeue
        }
        dest.sendMessage(toDeliver); // message is delivered here
    //    inTransit--;
    //}
    //}).start();
  }

  public int getDelay() {
    int randomInt = new Random().nextInt(100);
    return randomInt;
  }

  public String toString() {
    return "edge (" + a.UID + "," + b.UID + ")";
  }
}
