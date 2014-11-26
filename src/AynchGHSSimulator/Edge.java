package AynchGHSSimulator;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.LinkedList;

/**
 * Created by priyanka on 11/25/14.
 */
public class Edge {
  Node a, b;
  int edgeID;
  int cost;
  int inTransit;
  boolean isBranch;
  LinkedList aQueue, bQueue;

  Edge(Node a, Node b) {
    edgeID = MSTviewer.edgeCount++;
    this.a = a;
    this.b = b;
    a.getNeighbors().put(b,this);
    b.getNeighbors().put(a,this);
    aQueue = new LinkedList(); // ensures FIFO delivery from a to b
    bQueue = new LinkedList(); // ensures FIFO delivery from b to a
  }

  int getCost() { // use Euclidian distance, with unique low order bits
    return this.a.UID + this.b.UID;
  }

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
          this.setForeground(TRANSLUCENT_GRAY);
        break;
      case Message.CONNECT:
        this.setForeground(Color.white); this.repaint();
        break;
      case Message.INITIATE:
      case Message.INFORM:
        isBranch = true;
        this.setForeground(Color.red); this.repaint();
        dest.level = m.level;
        dest.core = m.core;
        dest.updateLabel();
    }
    (new Thread() {
      public void run() { // animation from src to dest, then delivery
        if (getDelay() > 0) {
          try {
            Thread.sleep(getDelay()*20*priorCount);
          } catch (InterruptedException ie) {}
        }
        Message toDeliver;
        synchronized (messageQueue) {
          toDeliver = (Message)  messageQueue.removeFirst(); //dequeue
        }
        dest.sendMessage(toDeliver); // message is delivered here
        inTransit--;
      }
    }).start();
  }

  public String toString() {
    return "edge (" + a.UID + "," + b.UID + ")";
  }
}
