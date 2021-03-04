package com.merman.celebrity.server;

import java.io.IOException;
import java.nio.ByteBuffer;


public interface IOutputSender {
	public void sendOutput(ByteBuffer aBuffer) throws IOException;
}
