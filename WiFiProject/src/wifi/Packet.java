package wifi;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;  // Should help with checksum calculations

/**
 * The Packet class creates 802.11 packets using bitwise operations, byte arrays, and 
 * some methods from the Java ByteBuffer class. Each Packet object remembers its
 * type, resend value, sequence number, destination address, source address, data bytes, 
 * checksum, and length. These pieces of information are stored as fields using the data
 * types I found intuitive. (The resend value, for example, is stored as a boolean since 
 * it can only ever be a 1 or a 0, the data is stored as a byte array, and the 16-bit MAC 
 * addresses are stored as shorts.)
 * 
 * To get a byte array that represents all of these fields (what you probably think of
 * as a packet) you must first use one of the two constructors to create a packet object 
 * and then call the public getPacket method on that object. Similarly, if you want any of
 * the changes made to a packet object via the setter methods to be reflected in it's byte
 * array representation, you must call the getPacket method after your setter calls. 
 * 
 * The class below is partitioned into 3 sections. The first contains constructors, the 
 * second contains public getters/setters, and the last contains private calculators/
 * converters. There are comments describing each section and each method. Please let me 
 * know if you have any questions. 
 * 
 * Notes:
 * 	I have not implemented the checksum calculations yet but I found and imported a class
 * 	that should make that quite easy. My issue is I'm not sure where/when I should perform
 *  the calculation. I'm open to your input on this, but I'll probably reach out to Brad
 *  as well.
 *  
 *  The main method is just for testing. I haven't done the most extensive tests yet, but
 *  I've put about 10 hours into this 450-line godforsaken hell hole, and I'm burnt out at 
 *  the moment. Definitely let me know if you find any bugs.
 * 
 * @author Darian
 * @date 11.6.2019
 */
public class Packet {
	private String type; 	// Unsigned 3-bit short representing the packet type
	private boolean resend; // Boolean representing whether the packet has been sent before
	private short seq;	   	// Unsigned 12-bit short sequence number
	private short destAddr; // Unsigned 16-bit short destination address
	private short srcAddr;  // Unsigned 16-bit short source address
	private byte[] data;    // The byte array of data
	private int chksum;   	// The 4-byte checksum
	private int len;	    // The length of the packet

	// Main Method (ONLY FOR TESTING)
	/**public static void main(String[] args) {
		
		// Here is some test data
		byte[] test = {1,2,3,4,5,6,7,8,9,10};
		
		// Here is the first constructor in action
		Packet p = new Packet("Data", false, (short)3003, (short)12345, (short)24567, test, 20);
		System.out.println(p.toString());
		
		// Here is the second constructor in action - it uses the byte array representation
		// of the packet produced by the first constructor to create another packet. 
		Packet b = new Packet(p.getPacket());
		System.out.println(b.toString());
		
		// I haven't found any bugs yet...
	}**/

///////////////////////////  Constructors  //////////////////////////////////////////////////////////////////////
// There are 2 constructors for the packet class. The first one is designed to be used //////////////////////////
// when sending packets (when you already know the information), and the second one is //////////////////////////
// designed to be used when receiving packets (when you just have a byte array).	   //////////////////////////
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////	
	
	/**
	 * Constructs an (outgoing) packet from all the provided values. 
	 * (Use in send when you know the values of components but not their corresponding bytes)
	 * 
	 * @param too damn many, should be self explanatory anyway
	 */
	public Packet(String type, boolean resend, short seq, short destAddr,  short srcAddr, byte[] data, int len) {
		
		// Update fields
		this.type = type;
		this.resend = resend;
		this.seq = seq;
		this.destAddr = destAddr;
		this.srcAddr = srcAddr;
		this.data = data;
		this.chksum = -1; // Still unsure how to implement checksum
		this.len = len;
	}

	/**
	 * Constructs a packet object from an (incoming) array of bytes.
	 * (Use in receive when you have the byte array but not its corresponding values)
	 * 
	 * @param packet - a byte array representation of a packet
	 */
	public Packet(byte[] packet) {
		
		// Update Fields by calculating values from byte array
		this.type = calcType(packet);
		this.resend = calcResend(packet);
		this.seq = calcSeq(packet);
		this.destAddr = bytesToShort(packet, 2, 3);
		this.srcAddr = bytesToShort(packet, 4, 5);
		this.data = calcData(packet);
		this.chksum = bytesToInt(packet, packet.length-4, packet.length);
		len = packet.length;
	}

///////////////////////////  Getters & Setters ////////////////////////////////////////////////////////////////
// These are getters and setters for each field in the Packet class. Changes made to a  ///////////////////////
// packet object with setters will not be reflected in it's byte[] representation until ///////////////////////
// you call packet.getPacket() because these methods only change the object's fields.   ///////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * The getType method returns the type of the packet.
	 * The types and their corresponding binary representations are as follows:
	 * 		Data 	000
	 * 		ACK		001
	 * 		Beacon	010
	 * 		CTS		100
	 * 		RTS		101
	 */
	public String getType() {
		return type;
	}
	
	/**
	 * The setType method changes the type of the packet to the provided string.
	 * It relies on you using strings exactly equal to Data ACK Beacon CTS or RTS.
	 * We may want to change this to an enumerated type in future...
	 */
	public void setType(String s) {
		type = s;
	}
	
	/**
	 * The getResend method returns true if the packet has been sent before.
	 */
	public boolean getResend() {
		return resend;
	}
	
	/**
	 * The setResend method sets resend to true, indicating that the packet has
	 * been sent before. In order for this change to be reflected in the actual byte[]
	 * you will need to call getPacket() again after setResend().
	 */
	public void setResend() {
		resend = true;
	}
	
	/**
	 * The getDestADDr method returns the destination address of the packet.
	 */
	public short getDestAddr() {
		return destAddr;
	}
	
	/**
	 * The setDestAddr method sets the destination address of the packet to the provided
	 * short.
	 */
	public void setDestAddr(short a) {
		destAddr = a;
	}
	
	/**
	 * The getSrcADDr method returns the source address of the packet.
	 */
	public short getSrcAddr() {
		return srcAddr;
	}
	
	/**
	 * The setSrcAddr method sets the destination address of the packet to the provided
	 * short.
	 */
	public void setSrcAddr(short a) {
		srcAddr = a;
	}
	
	/**
	 * The getData method returns the bytes in the packet which contain the data.
	 * 
	 * @return byte[] data - the data
	 */
	public byte[] getData() {
		return data;
	}
	
	/**
	 * The setData method sets the data field byte array to the provided byte array.
	 */
	public void setData(byte[] d) {
		data = d;
	}
	
	/**
	 * The getChksum method returns the checksum of the packet
	 */
	public int getChksum() {
		return chksum;
	}
	
	/**
	 * The setChksum method sets the packet's checksum field to the provided integer
	 */
	public void setChksum(int c) {
		chksum = c;
	}
	
	/**
	 * The getPacket method returns a byte array representation of the packet by converting 
	 * each of the fields into their byte representations, putting those bytes into smaller
	 * sub-arrays, and placing those sub-arrays into a larger packet array. 
	 */
	public byte[] getPacket() {
		
		// Allocate array of proper length
		byte[] packet = new byte[len]; 
		int dataLen = len - 10; 	// Used to insert data bytes into packet

		// Produce Component Byte Arrays from Shorts and Data
		short control = calcControl(type, resend, seq);
		byte[] controlBytes = shortToBytes(control);
		byte[] destAddrBytes = shortToBytes((short) destAddr);
		byte[] srcAddrBytes = shortToBytes((short) srcAddr);
		byte[] chksumBytes = intToBytes(chksum);   
		
		// Fill Packet with Component Arrays
		place(packet, controlBytes, 0, 2);	  // Put control bytes into first two bytes of packet
		place(packet, destAddrBytes, 2, 2);   // Put destination address in bytes 2 & 3
		place(packet, srcAddrBytes, 4, 2);    // Put source address in bytes 4 and 5
		place(packet, data, 6, dataLen);  	  // Put data in bytes 6-?
		place(packet, chksumBytes, (6+dataLen), 4); // Put checksum in last 4 bytes of packet

//		// Calculate Checksum 		I DON'T KNOW IF THIS IS WHERE TO CALCULATE THE CHECKSUM	
//			CRC32 c = new CRC32();
//			c.update(packet);
//			chksum = (int) c.getValue();

		return packet;
	}
	
	/**
	 * The second constructor is equivalent to a "setPacket" method...
	 */
	
///////////////////////////  Calculators & Converters ////////////////////////////////////////////////////////
// The calculator and converter methods are mainly used in the second packet constructor. ////////////////////
// They allow you to decipher the various packet fields from an array of bytes by         ////////////////////
// converting between data types and doing bitwise operations.							  ////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	/**
	 * The calcType method calculates the String type of a packet given its byte array 
	 * representation
	 */
	private String calcType(byte[] p) {
		String type = "";
		short ctrl = bytesToShort(p, 0, 1);			//Converts control bytes into short
			ctrl = (short) (ctrl & 0xFFFF);			//Removes sign extension
			ctrl = (short) ((ctrl >> 13) & 0xFFFF); //Isolates top 3 bits of control
		if(ctrl == 0){
			type = "Data";
		}
		if(ctrl == 1){
			type = "ACK";
		}
		if(ctrl == 2) {
			type = "Beacon";
		}
		if(ctrl == 4){
			type = "RTS";
		}
		if(ctrl == 5) {
			type = "CTS";
		}
		return type;
	}
	
	/**
	 * The calcResend method calculates the boolean resend value of a packet given its 
	 * byte array representation
	 */
	private boolean calcResend(byte[] p) {
		short ctrl = bytesToShort(p, 0, 1); //Convert control bytes into short
		ctrl = (short) (ctrl >> 12); 		//Isolate top 4 bits
		if(ctrl % 2 == 0) {					//If 4 bits are even, lowest bit is 0, so
			return false;					//Not a resend
		}
		else {
			return true;					//Otherwise must be odd and therefore a resend
		}
	}
	
	/**
	 * The calcSeq method calculates the short sequence value of a packet given its
	 * byte array representation
	 */
	private short calcSeq(byte[] p) {
		short ctrl = bytesToShort(p, 0, 1); //Convert control bytes into short
		ctrl = (short) (ctrl & 0xFFF); 		//Isolate bottom 12 bits of control 
		return ctrl;
	}
	
	/**
	 * The calcData method isolates the bytes containing data from a packet byte[] and
	 * returns them.
	 */
	private byte[] calcData(byte[] p) {
		int dataLen = p.length-10;			//10 bytes are control addresses and checksum
		byte[] data = new byte[dataLen];	
		for (int i = 6; i < (6+dataLen); i++) { //Place packet data into array
			data[i-6] = p[i];
		}
		return data;
	}
	
	/**
	 * The calcControl method calculates the 16 control bits at the beginning of a
	 * packet by combining the packet type, resend value, and sequence number. It is
	 * used in the getPacket method.
	 * 
	 * @param type		- Data; ACK; Beacon; CTS; RTS
	 * @param resend	- true = resend; false = first time
	 * @param seq		- 12-bit short 
	 * @return control	- 16-bit short in the formant: 3 type bits , 1 resend bit, 12 sequence bits
	 */
	private short calcControl(String type, boolean resend, short seq) {
		short temp = 0;
		if(type.equals("Data")){
			temp = 0;
		}
		if(type.equals("ACK")){
			temp = 1;
		}
		if(type.equals("Beacon")){
			temp = 2;
		}
		if(type.equals("RTS")){
			temp = 4;
		}
		if(type.equals("CTS")){
			temp = 5;
		}
		short control = (short) (temp & 0xFFFF);
			  control = (short) ((control << 13) | seq & 0xFFF);
			  if (resend) {
				  control = (short) ((short) control | 4096); //Flips x bit 000x000000000000
			  }
		return control;
	}
	
	/**
	 * The intToBytes method uses the ByteBuffer class to break an integer into its 
	 * 4 respective bytes and then returns them as an array of bytes. 
	 * 
	 * @param i 	 - an int
	 * @return bytes - an array of the 4 bytes that made up the short
	 */
	private byte[] intToBytes(int i) {
		ByteBuffer bytes = ByteBuffer.allocate(4);
		bytes.order(ByteOrder.BIG_ENDIAN); 		// most significant byte placed at [0]
		bytes.putInt(i);
		return bytes.array();
	}
	
	/**
	 * The bytesToInt method takes 4 bytes (i-j) in a byte array and combines them to 
	 * form a 4-byte int.
	 * 
	 * @param b    	- a byte array
	 * @return time - an long representation of the time from the packet
	 */
	private int bytesToInt(byte[] b, int j, int k) {
		int temp = ((int)b[j]) & 0xFF; 					  //Casts the third byte into a long 
		for (int i=(j+1); i<k; i++) {
			temp = (temp << 8) |  (((int)b[(i)]) & 0xFF); //Repeatedly appends the other bytes 
		}
		return temp;
	}
	
	/**
	 * The shortToBytes method uses the ByteBuffer class to break a short into its 
	 * 2 respective bytes and returns them as an array of bytes. 
	 * 
	 * @param s 	 - a short
	 * @return bytes - an array of the 2 bytes that made up the short
	 */
	private byte[] shortToBytes(short s) {
		ByteBuffer bytes = ByteBuffer.allocate(2);
		bytes.order(ByteOrder.BIG_ENDIAN); 		// most significant byte placed at [0]
		bytes.putShort(s);
		return bytes.array();
	}
	
	/**
	 * The bytesToShort method takes two bytes (i and j) from an array and converts them 
	 * into a short.
	 * 
	 * @param b     - a byte array
	 * @param i		- the byte for the top 8 bits of the short
	 * @param j		- the byte for the bottom 8 bits of the short
	 * @return shrt - a 16-bit short
	 */
	private short bytesToShort(byte[] b, int i, int j) {
		short shrt = (short) ((b[i]) & 0xFF); 			// Places first byte in bottom 8 bits of short
		shrt = (short) ((shrt << 8) | ((b[j]) & 0xFF));	// Shifts that byte left and paces next byte in bottom 8 bits
		return shrt;
	}
	
	/** 
	 * Places the first num bytes from array b into the array a starting at the offset off
	 * 
	 * @param a 	- array to place into
	 * @param b 	- array to place from
	 * @param off	- where to start adding into packet
	 * @param num	- how many bytes to take from b
	 */
	private void place(byte[] a, byte[] b, int off, int num) {
		for (int i = off; i < (off+num); i++) {
			a[i] = b[i-off];
		}
	}
	
	/**
	 * Returns a string representation of the packet byte array
	 */
	public String toString() {
		String str = "Type: "+type+
					 "\nResend: "+resend+
					 "\nSeq: "+seq+
					 "\nDestAddr: "+destAddr+
					 "\nSrcAddr: "+srcAddr+
					 "\nData: "+data.length+" bytes "+
					 "\nChksum: "+chksum+"\n[ ";
		byte[] packet = getPacket();
		for (int i = 0; i < len; i++) {
			str += (packet[i] & 0xFF) + " ";
		}
		str += "]\n";
		return str;
	}

}
