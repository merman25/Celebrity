package com.merman.celebrity.tests;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import com.merman.celebrity.server.WebsocketMessage;
import com.merman.celebrity.server.WebsocketUtil;

public class WebsocketMessageTest {

	private static String message = "Ĥéłŀö Ŵøŗŀđ";
	private static byte[] mask = { 1, 2, 3, 4 }; // I use the same combination on my luggage
	
	private static byte[] createFrame() {
		byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
		int messageLength = messageBytes.length;
		byte[] lengthArray = WebsocketUtil.toLengthArray(messageLength);
		
		byte[] encodedMessageBytes = new byte[messageLength];
		for (int byteIndex = 0; byteIndex < encodedMessageBytes.length; byteIndex++) {
			encodedMessageBytes[byteIndex] = (byte) ( messageBytes[byteIndex] ^ mask[byteIndex & 0x3] );
		}
		
		byte[] frame = new byte[WebsocketUtil.NUM_BYTES_BEFORE_LENGTH_ARRAY + lengthArray.length + mask.length + messageLength];
		
		int offset = 0;
		frame[offset] = WebsocketUtil.MESSAGE_START_BYTE;
		offset += 1;
		System.arraycopy(lengthArray, 0, frame, offset, lengthArray.length);
		offset += lengthArray.length;
		System.arraycopy(mask, 0, frame, offset, mask.length);
		offset += mask.length;
		System.arraycopy(encodedMessageBytes, 0, frame, offset, messageLength);
		offset += messageLength;
		
		return frame;
	}
	
	@Test
	public void testSendingStringAllAtOnce() {
		byte[] frame = createFrame();
		WebsocketMessage websocketMessage = new WebsocketMessage();
		websocketMessage.addBytes(frame);
		
		Assert.assertEquals("Message received and decoded correctly", "Ĥéłŀö Ŵøŗŀđ", websocketMessage.getDecodedMessage());
	}
	
	@Test
	public void testSendingInSmallChunks() {
		/* It's also possible for a message to be sent in multiple frames, and then
		 * you have to check that the FIN bit (first bit of the frame) is set to indicate
		 * that it's the last frame.
		 * 
		 * Not gonna bother supporting that, don't think it's gonna happen for the small
		 * messages my app sends. This tests a different case, slow connection so the bytes
		 * from a single frame don't arrive all at once
		 */
		
		// 1 byte at a time
		byte[] frame = createFrame();
		Assert.assertEquals("Frame array length", 27, frame.length);
		WebsocketMessage websocketMessage = new WebsocketMessage();
		for (int byteIndex = 0; byteIndex < frame.length; byteIndex++) {
			byte[] inputArr = new byte[1];
			System.arraycopy(frame, byteIndex, inputArr, 0, 1);
			
			websocketMessage.addBytes(inputArr);
			
			if (byteIndex < 1) {
				Assert.assertEquals("Unknown length array length", -1, websocketMessage.getLengthOfLengthArray());
				Assert.assertEquals("Unknown message length", -1, websocketMessage.getReportedMessageLength());
			}
			else {
				Assert.assertEquals("Length array length", 1, websocketMessage.getLengthOfLengthArray());
				Assert.assertEquals("Message length", 21, websocketMessage.getReportedMessageLength());
			}
			
			if (byteIndex < frame.length - 1) {
				Assert.assertEquals("Unknown message", null, websocketMessage.getDecodedMessage());
			}
		}
		
		Assert.assertEquals("Message received and decoded correctly", "Ĥéłŀö Ŵøŗŀđ", websocketMessage.getDecodedMessage());
		
		// Any number of bytes at a time, upto 26
		for (int numBytesAtOnce = 1; numBytesAtOnce < frame.length; numBytesAtOnce++) {
			websocketMessage = new WebsocketMessage();
			for (int byteIndex = 0; byteIndex < frame.length;) {
				int numBytes = Math.min(numBytesAtOnce, frame.length - byteIndex);
				byte[] inputArr = new byte[numBytes];
				System.arraycopy(frame, byteIndex, inputArr, 0, numBytes);
				
				websocketMessage.addBytes(inputArr);
				byteIndex += numBytes;

				if (byteIndex <= 1) {
					Assert.assertEquals("Unknown length array length", -1, websocketMessage.getLengthOfLengthArray());
					Assert.assertEquals("Unknown message length", -1, websocketMessage.getReportedMessageLength());
				}
				else {
					Assert.assertEquals("Length array length", 1, websocketMessage.getLengthOfLengthArray());
					Assert.assertEquals("Message length", 21, websocketMessage.getReportedMessageLength());
				}
				
				if (byteIndex < frame.length - 1) {
					Assert.assertEquals("Unknown message", null, websocketMessage.getDecodedMessage());
				}
			}
			Assert.assertEquals("Message received and decoded correctly", "Ĥéłŀö Ŵøŗŀđ", websocketMessage.getDecodedMessage());
		}
	}
}
