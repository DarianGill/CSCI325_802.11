package wifi;
import java.util.concurrent.ArrayBlockingQueue;
import rf.RF;


public class Sender implements Runnable {

	
	private Integer theState;
	private RF theRF;
	private ArrayBlockingQueue<Packet> packets;
	private ArrayBlockingQueue<Packet> acks;
	private Packet packToSend;
	
	//take in a queue and manipulate that queue in the send method, will that hold for this to pull from??
	public Sender(RF theRF, ArrayBlockingQueue<Packet> packets, ArrayBlockingQueue<Packet> acks) {///take in RF so it can send, a queue that will be manipulated w/ data, what else?
		this.packets = packets;
		this.acks = acks;
		this.theRF = theRF;
		this.theState = 0;
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		//loop through switch statement to determine where in flowchart we are, do different stuff in each section of the switch statement
		//call packet class and make packet
		//take in new data somehow
		//eventually send the data
		int difs = RF.aSIFSTime + (2*RF.aSlotTime);
		boolean used = true;
		
		//some kind of looping mechanism here??
		
		switch(theState) {	//the state is an integer corresponding to the case to enter
			case 0:
			//waiting for packet
				if(packets.peek() != null) {	//need to see if channel is busy right here to see if sending after difs
					used = this.theRF.inUse();	//see if channel is busy at get data
					packToSend = packets.poll();
					theState = 1;
					try {
						Thread.sleep(difs);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}	
				}
			case 1:	//has packet and waited DIFS, check if idle and if idle when started
			//just to see the format
				if(!used) {
					if(!this.theRF.inUse()) {	//not used before or after
						this.theRF.transmit(packToSend.getPacket());
					}
				}
			case 2:
		//eventually peek at the acks to see if there is an ack for me!
		}
		
		
		//take packet from send() and go through waiting stuff, send! and ack recieval
		
	}

}
