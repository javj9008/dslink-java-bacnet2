package bacnet;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.bacnet4j.util.sero.StreamUtils;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

public class SerialInputStream extends InputStream implements SerialPortEventListener {

	private final Logger LOGGER = LoggerFactory.getLogger(SerialInputStream.class);
	private final Object closeLock = new Object();
	private volatile boolean closed = false;
	protected LinkedBlockingQueue<Byte> dataStream;
	protected Object dataStreamLock = new Object();
	protected SerialPort port;

	public SerialInputStream(SerialPort serialPort) throws SerialPortException {
		this.dataStream = new LinkedBlockingQueue<Byte>();

		this.port = serialPort;
		this.port.addEventListener(this, SerialPort.MASK_RXCHAR);
	}

	@Override
	public int read() throws IOException {
		synchronized (dataStreamLock) {
			try {
				if (dataStream.size() > 0)
					return dataStream.take() & 0xFF; // Return unsigned byte
														// value by masking off
														// the high order bytes
														// in the returned int
				else
					return -1;
			} catch (InterruptedException e) {
				throw new IOException(e);
			}
		}
	}

	@Override
	public int available() throws IOException {
		synchronized (dataStreamLock) {
			return this.dataStream.size();
		}
	}

	public int peek() {
		return this.dataStream.peek();
	}

	public void closeImpl() throws IOException {
		try {
			this.port.removeEventListener(); // Remove the listener
		} catch (jssc.SerialPortException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void close() throws IOException {
		if (LOGGER.isDebugEnabled())
			LOGGER.debug("Attempting Close of Serial Port Input Stream.");
		synchronized (closeLock) {
			if (closed) {
				return;
			}
			closeImpl();
			closed = true;
		}
		if (LOGGER.isDebugEnabled())
			LOGGER.debug("Closed Serial Port Input Stream.");
	}

	@Override
	public void serialEvent(SerialPortEvent event) {
		if (event.isRXCHAR()) {// If data is available
			if (LOGGER.isDebugEnabled())
				LOGGER.debug("Serial Receive Event fired.");
			// Read the bytes, store into queue
			try {
				synchronized (dataStreamLock) {
					byte[] buffer = this.port.readBytes(event.getEventValue());
					for (int i = 0; i < buffer.length; i++) {
						this.dataStream.put(buffer[i]);
					}
					if (LOGGER.isDebugEnabled()) {
						LOGGER.debug("Recieved: " + StreamUtils.dumpHex(buffer, 0, buffer.length));
					}
				}

			} catch (Exception e) {
				LOGGER.error("", e);
			}

		} // end was RX event
		else {
			if (LOGGER.isDebugEnabled())
				LOGGER.debug("Non RX Event Type Recieved: " + event.getEventType());
		}
	}

}
