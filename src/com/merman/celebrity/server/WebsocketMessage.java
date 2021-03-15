package com.merman.celebrity.server;

import java.nio.charset.StandardCharsets;

import com.merman.celebrity.server.exceptions.WebsocketMessageException;
import com.merman.celebrity.server.exceptions.WebsocketMessageTooLongException;

public class WebsocketMessage {
	// 10 kB. Spec allows lengths of upto 16 EB (2^64 bytes), or at least 8 EB (Long.MAX_VALUE bytes), but not gonna bother trying to support that
	private static int MAX_SUPPORTED_MESSAGE_LENGTH_IN_BYTES = 10 * 1024;
	
	// array can grow if necessary
	private byte[] bytes = new byte[WebsocketUtil.MAX_BYTES_IN_FRAME_HEADER];
	private int offset;
	private int reportedMessageLength = -1;
	private int lengthOfLengthArray = -1;
	
	/* Technically you can also send binary data down a websocket.
	 * Not supported by this server.
	 */
	private String decodedMessage;
	
	public void addBytes(byte[] aBytes) {
		int inputArrayOffset = 0;
		if (offset + aBytes.length - inputArrayOffset > bytes.length) {
			if (reportedMessageLength == -1) {
				// We def have at least enough bytes to make a length array, so let's do that and check the length before expanding
				int numBytesToInitiallyCopy = bytes.length - offset;
				System.arraycopy(aBytes, 0, bytes, offset, numBytesToInitiallyCopy);
				offset = bytes.length;
				inputArrayOffset = numBytesToInitiallyCopy;
				
				reportedMessageLength = retrieveAndCheckMessageLengthFromArray(bytes);
				lengthOfLengthArray = lengthOfLengthIndicatorPart(bytes);
				
				int requiredArrayLength = requiredBufferSize();
				if (bytes.length < requiredArrayLength) {
					byte[] newByteArr = new byte[requiredArrayLength];
					System.arraycopy(bytes, 0, newByteArr, 0, offset);
					bytes = newByteArr;
				}
				
				int bytesRemainingInInputArray = aBytes.length - inputArrayOffset;
				int bufferRemainingCapacity = bytes.length - offset;
				int numBytesToCopy = Math.min(bytesRemainingInInputArray, bufferRemainingCapacity);
				System.arraycopy(aBytes, numBytesToInitiallyCopy, bytes, offset, numBytesToCopy);
				offset += numBytesToCopy;
				inputArrayOffset += numBytesToCopy;
			}
			
			
			if (offset + aBytes.length - inputArrayOffset > bytes.length) {
				// Technically could happen if we get multiple messages all at once. Shouldn't happen in this app
				throw new WebsocketMessageException(String.format("Number of bytes received [%,d] exceeds reported message length [%,d]", offset + aBytes.length, reportedMessageLength));
			}
		}
		else {
			System.arraycopy(aBytes, 0, bytes, offset, aBytes.length);
			offset += aBytes.length;
			
			if (reportedMessageLength == -1) {
				if (lengthAvailable(bytes, offset)) {
					reportedMessageLength = retrieveAndCheckMessageLengthFromArray(bytes);

					assert lengthOfLengthArray > 0 : "lengthOfLengthArray field must be set, since lengthAvailable returned true";

					int requiredArrayLength = requiredBufferSize();
					if (bytes.length < requiredArrayLength) {
						byte[] newByteArr = new byte[requiredArrayLength];
						System.arraycopy(bytes, 0, newByteArr, 0, offset);
						bytes = newByteArr;
					}
				}
			}
		}
		
		if (reportedMessageLength >= 0
				&& offset >= requiredBufferSize()) {
			decodeMessage();
		}
	}

	private void decodeMessage() {
		byte[] decodedByteArray = WebsocketUtil.decode(bytes, WebsocketUtil.NUM_BYTES_BEFORE_LENGTH_ARRAY + lengthOfLengthArray, bytes, WebsocketUtil.NUM_BYTES_BEFORE_LENGTH_ARRAY + lengthOfLengthArray + WebsocketUtil.NUM_BYTES_IN_MASK);
		decodedMessage = new String(decodedByteArray, StandardCharsets.UTF_8);
	}

	private int requiredBufferSize() {
		return WebsocketUtil.NUM_BYTES_BEFORE_LENGTH_ARRAY + lengthOfLengthArray + WebsocketUtil.NUM_BYTES_IN_MASK + reportedMessageLength;
	}

	private int retrieveAndCheckMessageLengthFromArray(byte[] aBytes) {
		long messageLengthFromArray = WebsocketUtil.toLength(aBytes, WebsocketUtil.NUM_BYTES_BEFORE_LENGTH_ARRAY);
		if (messageLengthFromArray > MAX_SUPPORTED_MESSAGE_LENGTH_IN_BYTES) {
			throw new WebsocketMessageTooLongException(String.format("Reported length: %,d. Max length: %,d", messageLengthFromArray, MAX_SUPPORTED_MESSAGE_LENGTH_IN_BYTES));
		}
		if (messageLengthFromArray < 0) {
			throw new WebsocketMessageException("Reported length less than 0: " + messageLengthFromArray);
		}
		return (int) messageLengthFromArray;
	}

	/**
	 * Checks the second <code>byte</code> of <code>aBytes</code> to determine how many bytes are involved in reporting
	 * the length of the WebsocketFrame.
	 * @param aBytes
	 * @return The number of bytes involved in reporting the length of the WebsocketFrame.
	 */
	private int lengthOfLengthIndicatorPart(byte[] aBytes) {
		int lengthOfLengthArray = 	aBytes[WebsocketUtil.NUM_BYTES_BEFORE_LENGTH_ARRAY] < WebsocketUtil.LENGTH_MAGNITUDE_16_BIT_INDICATOR  ? 	1 :
									aBytes[WebsocketUtil.NUM_BYTES_BEFORE_LENGTH_ARRAY] == WebsocketUtil.LENGTH_MAGNITUDE_16_BIT_INDICATOR ? 	3 :
																									WebsocketUtil.MAX_BYTES_IN_LENGTH_ARRAY;
		return lengthOfLengthArray;
	}
	
	private boolean lengthAvailable(byte[] aBytes, int aOffset) {
		if (aOffset < 2) {
			return false; // Only has 0 or 1 bytes, not enough
		}
		else {
			lengthOfLengthArray = lengthOfLengthIndicatorPart(aBytes);
			
			boolean lengthAvailable = aOffset >= WebsocketUtil.NUM_BYTES_BEFORE_LENGTH_ARRAY + lengthOfLengthArray;
			
			return lengthAvailable;
		}
	}

	public int getReportedMessageLength() {
		return reportedMessageLength;
	}

	public String getDecodedMessage() {
		return decodedMessage;
	}

	public int getLengthOfLengthArray() {
		return lengthOfLengthArray;
	}
}
