package org.rg.game.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.channels.FileChannel;

import org.burningwave.Throwables;

import com.fasterxml.jackson.databind.ObjectMapper;

public class IOUtils {
	public static final IOUtils INSTANCE = new IOUtils();
	final ObjectMapper objectMapper = new ObjectMapper();


	public void copy(InputStream input, OutputStream output) {
		try {
			byte[] buffer = new byte[1024];
			int bytesRead = 0;
			while (-1 != (bytesRead = input.read(buffer))) {
				output.write(buffer, 0, bytesRead);
			}
		} catch (IOException e) {
			Throwables.INSTANCE.throwException(e);
		}
	}

	public File writeToNewFile(String absolutePath, String value) {
		try (FileChannel outChan = new FileOutputStream(absolutePath, true).getChannel()) {
		  outChan.truncate(0);
		} catch (IOException exc) {
			//LogUtils.INSTANCE.error(exc);
		}
		try (FileWriter fileWriter = new FileWriter(absolutePath, false);) {
			fileWriter.write(value);
			fileWriter.flush();
		} catch (IOException exc) {
			Throwables.INSTANCE.throwException(exc);
		}
		return new File(absolutePath);
	}

	public byte[] serialize(Serializable object) throws IOException {
		try (ByteArrayOutputStream bAOS = new ByteArrayOutputStream(); ObjectOutputStream oOS = new ObjectOutputStream(bAOS);) {
	        oOS.writeObject(object);
	        return bAOS.toByteArray();
		}
	}

	public void store(String key, Serializable object, String basePath) {
		try (
			FileOutputStream fout = new FileOutputStream(basePath + "/" + key /*Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8))*/ + ".ser");
			ObjectOutputStream oos = new ObjectOutputStream(fout)
		) {
			oos.writeObject(object);
			LogUtils.INSTANCE.info("Object with id '" + key + "' stored in the physical cache");
		} catch (Throwable exc) {
			Throwables.INSTANCE.throwException(exc);
		}
	}

	public <T extends Serializable> T load(String key, String basePath) {
		try (FileInputStream fIS = new FileInputStream(basePath + "/" + key /*Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8))*/ + ".ser");
			ObjectInputStream oIS = new ObjectInputStream(fIS)) {
			T effectiveItem = (T) oIS.readObject();
			//LogUtils.INSTANCE.info("Object with id '" + key + "' loaded from physical cache" /*+ ": " + effectiveItem*/);
	        return effectiveItem;
		} catch (FileNotFoundException exc) {
			return null;
		} catch (Throwable exc) {
			return Throwables.INSTANCE.throwException(exc);
		}
	}

	public <T> T readFromJSONFormat(File premiumCountersFile, Class<T> cls) {
		try {
			return objectMapper.readValue(premiumCountersFile, cls);
		} catch (IOException exc) {
			return Throwables.INSTANCE.throwException(exc);
		}
	}

	public void writeToJSONFormat(File premiumCountersFile, Object object) {
		try {
			objectMapper.writeValue(premiumCountersFile, object);
		} catch (IOException exc) {
			Throwables.INSTANCE.throwException(exc);
		}
	}

	public void writeToJSONPrettyFormat(File premiumCountersFile, Object object) {
		try {
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(premiumCountersFile, object);
		} catch (IOException exc) {
			Throwables.INSTANCE.throwException(exc);
		}
	}

}
