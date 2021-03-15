package com.merman.celebrity.server;

import java.nio.charset.StandardCharsets;

public class WebsocketUtil {

	public static final byte            MESSAGE_START_BYTE                          = (byte) 0x81;                         // -127
	public static final int             MESSAGE_START_BYTE_AS_INT                   = 0x81;                                // 129

	// Can have a length value of upto 64 bits
	public static final int             MAX_BITS_IN_LENGTH_VALUE					= 64;
	
	/* A frame starts with one byte. First nibble is always 0x8, "unless an extension is negotiated that defines meanings
     * for non-zero values [of bits 1-3]" (RFC 6455). Second nibble is the opcode and defines the difference between
	 * MESSAGE_START_BYTE, CLOSE_CONNECTION_BYTE, PING_BYTE, PONG_BYTE, and others.
	 */
	public static final int             NUM_BYTES_BEFORE_LENGTH_ARRAY				= 1;
	
	/* A length array has 1 byte which is either the value of the length, or LENGTH_MAGNITUDE_16_BIT_INDICATOR,
	 * or LENGTH_MAGNITUDE_64_BIT_INDICATOR, followed by 0, 2, or 8 bytes.
	 */
	public static final int             MAX_BYTES_IN_LENGTH_ARRAY					= 1 + MAX_BITS_IN_LENGTH_VALUE / 8;
	public static final int 			MAX_BYTES_IN_FRAME_HEADER					= NUM_BYTES_BEFORE_LENGTH_ARRAY + MAX_BYTES_IN_LENGTH_ARRAY;
	public static final int 			NUM_BYTES_IN_MASK 							= 4;

	public static final int             LENGTH_BYTE_SUBTRACTION_CONSTANT            = 128;
	public static final byte            LENGTH_MAGNITUDE_16_BIT_INDICATOR           = 126;
	public static final byte            LENGTH_MAGNITUDE_64_BIT_INDICATOR           = 127;

	public static final int             MAX_LENGTH_16_BITS                          = 65536;

	public static final int             CLOSE_CONNECTION_BYTE                       = 0x88;
	public static final int             PING_BYTE                                   = 0x89;
	public static final int             PONG_BYTE                                   = 0x8A;

	public static final String          STOP                                        = "__STOP__";
	public static final String          PONG     									 = "__PONG__";
	public static final String          CLOSE_CONNECTION_MESSAGE                    = "03E9";

	private WebsocketUtil() {
	}
	
	public static byte[] toWebsocketFrame(byte aMessageStartByte, String aMessage) {
		byte[] messageBytes = aMessage.getBytes(StandardCharsets.UTF_8);
		byte[] lengthArray = WebsocketUtil.toLengthArray(messageBytes.length);

		byte[] frame = new byte[messageBytes.length + lengthArray.length + 1];
		frame[0] = aMessageStartByte;
		System.arraycopy(lengthArray, 0, frame, 1, lengthArray.length);
		System.arraycopy(messageBytes, 0, frame, lengthArray.length + 1, messageBytes.length);
		return frame;
	}

	public static byte[] decode(byte[] aKey, byte[] aEncodedMessage) {
		return decode(aKey, 0, aEncodedMessage, 0);
	}
	
	public static byte[] decode(byte[] aKey, int aKeyArrayOffset, byte[] aEncodedMessage, int aMessageArrayOffset) {
		byte[] decodedMessage = new byte[aEncodedMessage.length - aMessageArrayOffset];
		for (int i = 0; i < decodedMessage.length; i++) {
			decodedMessage[i] = (byte) (aEncodedMessage[i + aMessageArrayOffset] ^ aKey[aKeyArrayOffset + (i & 0x3)]);
		}
		return decodedMessage;
	}

	public static long toLength(byte[] aLengthByteArray, int aOffset) {
		long length = 0;
		int lengthMagnitudeIndicator = aLengthByteArray[aOffset];
		if ( lengthMagnitudeIndicator < 0 ) {
			throw new IllegalArgumentException("Illegal magnitude indicator: " + aLengthByteArray[aOffset]);
		}
		else if ( lengthMagnitudeIndicator < LENGTH_MAGNITUDE_16_BIT_INDICATOR ) {
			length = lengthMagnitudeIndicator;
		}
		else if ( lengthMagnitudeIndicator == LENGTH_MAGNITUDE_16_BIT_INDICATOR ) {
			int byteOneAsInt = aLengthByteArray[1 + aOffset];
			int byteTwoAsInt = aLengthByteArray[2 + aOffset];
			
			if ( byteOneAsInt < 0 ) {
				byteOneAsInt = 256 + byteOneAsInt;
			}
			if ( byteTwoAsInt < 0 ) {
				byteTwoAsInt = 256 + byteTwoAsInt;
			}
			length = ( ( byteOneAsInt << 8 ) | byteTwoAsInt );
		}
		else {
			assert aLengthByteArray[aOffset] == LENGTH_MAGNITUDE_64_BIT_INDICATOR;
			
			int bitsInLengthValue = MAX_BITS_IN_LENGTH_VALUE; // 64
			int bytesInLengthValue = bitsInLengthValue / 8;   // 8
			int bytesInLengthArray = bytesInLengthValue + 1;  // 9

			for (int i = 1; i < bytesInLengthArray; i++) {
				int shiftAmount = bytesInLengthValue * (bytesInLengthValue-i);
				long byteAsLong = aLengthByteArray[i + aOffset];
				if ( byteAsLong < 0 ) {
					byteAsLong = 256 + byteAsLong;
				}
				length |= ( byteAsLong << shiftAmount );
			}
		}
		return length;
	}
	
	public static byte[] toLengthArray(long aLength) {
		byte[] lengthArray;
		if ( aLength < LENGTH_MAGNITUDE_16_BIT_INDICATOR ) {
			lengthArray = new byte[1];
			lengthArray[0] = (byte) aLength;
		}
		else if ( aLength < MAX_LENGTH_16_BITS ) {
			lengthArray = new byte[3];
			lengthArray[0] = LENGTH_MAGNITUDE_16_BIT_INDICATOR;
			lengthArray[1] = (byte) (aLength >> 8);
			lengthArray[2] = (byte) (aLength & 255);
		}
		else {
			lengthArray = new byte[9];
			lengthArray[0] = LENGTH_MAGNITUDE_64_BIT_INDICATOR;
			for (int i = 1; i < 9; i++) {
				int shiftAmount = 8 * (8-i);
				lengthArray[i] = (byte) (aLength >> shiftAmount);
			}
		}
		
		return lengthArray;
	}
}