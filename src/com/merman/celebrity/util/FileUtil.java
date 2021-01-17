package com.merman.celebrity.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class FileUtil {

	private FileUtil() {
	}

	public static void deleteRecursively(Path aPath) throws IOException {
		if (Files.isDirectory(aPath)) {
			// Need to collect to list, a lambda can't throw an IOException
			List<Path> subFiles = Files.list(aPath).collect(Collectors.toList());
			for (Path path : subFiles) {
				deleteRecursively(path);
			}
		}
		
		Files.delete(aPath);
	}
}
