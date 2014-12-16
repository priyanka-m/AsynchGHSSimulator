package AsynchGHSSimulator;

import java.util.LinkedList;
import java.util.Random;
/**
* @author Shraddha Patel, Priyanka Menghani
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
  
  Edge(Node a, Node b, int weight) {
    edgeID = MSTviewer.edgeCount++;
    this.a = a;
    this.b = b;
    this.cost = weight;
    a.getNeighbors().put(b,this);
    b.getNeighbors().put(a,this);
    a.basicEdges.add(this);
    b.basicEdges.add(this);
    aQueue = new LinkedList(); // ensures FIFO delivery from a to b
    bQueue = new LinkedList(); // ensures FIFO delivery from b to a
    isBranch = false;
    isRejected = false;
  }

  public int getCost() {
    return this.a.UID + this.b.UID;
  }

  // Messages sent over an edge are forwarded to their destinations
  void forwardMessage(final Node src, final Node dest, final Message m) {
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
      case Message.ACCEPT:
        isBranch = true;
        break;
      case Message.REJECT:
        if (!isBranch){
          isRejected = true;
        }
        break;
      case Message.CONNECT:
        break;
      case Message.INITIATE:
        break;
      case Message.TEST:
        break;
      case Message.INFORM:
        isBranch = true;
        dest.level = m.level;
        dest.core = m.core;

    }
    (new Thread() {
      public void run() {
        if (getDelay() > 0) {
          try {
            Thread.sleep(getDelay()*1000*priorCount);
          } catch (InterruptedException ie) {}
        }
        Message toDeliver;
        synchronized (messageQueue) {
          toDeliver = (Message)  messageQueue.removeFirst(); //dequeue
        }
        dest.sendMessage(toDeliver); // message is delivered here
      }
    }).start();
  }

  public int getDelay() {
    int randomInt = new Random().nextInt(20);
    return randomInt;
  }

  @Override
  public String toString() {
    return "edge (" + a.UID + "," + b.UID + ")";
  }
}
