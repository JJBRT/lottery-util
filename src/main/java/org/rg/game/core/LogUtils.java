package org.rg.game.core;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.rg.game.lottery.engine.PersistentStorage;

public interface LogUtils {
	//public final static LogUtils INSTANCE = new LogUtils.ToConsole();
	public final static LogUtils INSTANCE = retrieveConfiguredLogger();

	static LogUtils retrieveConfiguredLogger() {
		String loggerType = EnvironmentUtils.getVariable("logger.type", "console");
		loggerType = "window";
		if (loggerType.equalsIgnoreCase("console")) {
			return new LogUtils.ToConsole();
		} else if (loggerType.equalsIgnoreCase("file")) {
			return LogUtils.ToFile.getLogger("default-log.txt");
		} else if (loggerType.equalsIgnoreCase("window")) {
			return new LogUtils.ToWindow();
		}
		throw new IllegalArgumentException(loggerType + " is not a valid logger type");
	}


	public void debug(String... reports);

	public void info(String... reports);

	public void warn(String... reports);

	public void error(String... reports);

	public void error(Throwable exc, String... reports);

	public static class ToConsole implements LogUtils {

		@Override
		public void debug(String... reports) {
			log(System.out, reports);
		}

		@Override
		public void info(String... reports) {
			log(System.out, reports);
		}

		@Override
		public void warn(String... reports) {
			log(System.err, reports);
		}

		@Override
		public void error(String... reports) {
			log(System.err, reports);
		}

		@Override
		public void error(Throwable exc, String... reports) {
			if (reports == null || reports.length == 0) {
				System.err.println();
			} else {
				for (String report : reports) {
					System.err.println(report);
				}
			}
			if (exc.getMessage() != null) {
				System.err.println(exc.getMessage());
			}
			for (StackTraceElement stackTraceElement : exc.getStackTrace()) {
				System.err.println("\t" + stackTraceElement.toString());
			}
		}

		private void log(PrintStream stream, String... reports) {
			if (reports == null || reports.length == 0) {
				stream.println();
				return;
			}
			for (String report : reports) {
				stream.println(report);
			}
		}
	}

	public static class ToFile implements LogUtils {
		public final static Map<String, ToFile> INSTANCES = new ConcurrentHashMap<>();
		private BufferedWriter writer;

		private ToFile(String absolutePath) {
			try {
				try (FileChannel outChan = new FileOutputStream(absolutePath, true).getChannel()) {
				  //outChan.truncate(0);
				} catch (IOException exc) {
					Throwables.sneakyThrow(exc);
				}
				writer = new BufferedWriter(new FileWriter(absolutePath, true));
			} catch (IOException exc) {
				Throwables.sneakyThrow(exc);
			}
		}

		public final static LogUtils getLogger(String relativePath) {
			String absolutePath =
				PersistentStorage.buildWorkingPath() + File.separator + (relativePath = relativePath != null? relativePath : "log.txt");
			return INSTANCES.computeIfAbsent(relativePath, key -> new ToFile(absolutePath));
		}

		@Override
		public void debug(String... reports) {
			for (int i = 0; i < reports.length; i++) {
				reports[i] = "[DEBUG] - " + reports[i];
			}
			log(reports);
		}

		@Override
		public void info(String... reports) {
			for (int i = 0; i < reports.length; i++) {
				reports[i] = "[INFO] - " + reports[i];
			}
			log(reports);
		}

		@Override
		public void warn(String... reports) {
			for (int i = 0; i < reports.length; i++) {
				reports[i] = "[WARN] - " + reports[i];
			}
			log(reports);
		}

		@Override
		public void error(String... reports) {
			for (int i = 0; i < reports.length; i++) {
				reports[i] = "[ERROR] - " + reports[i];
			}
			log(reports);
		}

		@Override
		public void error(Throwable exc, String... reports) {
			try {
				if (reports == null || reports.length == 0) {
					writer.append("\n");
				} else {
					for (String report : reports) {
						writer.append(report + "\n");
					}
				}
				if (exc.getMessage() != null) {
					writer.append(exc.getMessage() + "\n");
				}
				for (StackTraceElement stackTraceElement : exc.getStackTrace()) {
					writer.append("\t" + stackTraceElement.toString());
				}
			} catch (Throwable innerExc) {
				Throwables.sneakyThrow(exc);
			}
		}

		private void log(String... reports) {
			try {
				if (reports == null || reports.length == 0) {
					writer.append("\n");
					return;
				}
				for (String report : reports) {
					writer.append(report + "\n");
				}
				writer.flush();
			} catch (Throwable exc) {
				Throwables.sneakyThrow(exc);
			}
		}

	}

	public static class ToWindow implements LogUtils {
		private Logger logger;

		ToWindow() {
			logger = WindowHandler.attachNewWindowToLogger("logging.handler");
		}

		@Override
		public void debug(String... reports) {
			log(logger::fine, reports);
		}

		@Override
		public void info(String... reports) {
			log(logger::info, reports);
		}

		@Override
		public void warn(String... reports) {
			log(logger::warning, reports);
		}

		@Override
		public void error(String... reports) {
			log(logger::severe, reports);
		}

		@Override
		public void error(Throwable exc, String... reports) {
			if (reports == null || reports.length == 0) {
				logger.severe("\n");
			} else {
				for (String report : reports) {
					logger.severe(report + "\n");
				}
			}
			if (exc.getMessage() != null) {
				logger.severe(exc.getMessage() + "\n");
			}
			for (StackTraceElement stackTraceElement : exc.getStackTrace()) {
				logger.severe("\t" + stackTraceElement.toString() + "\n");
			}
		}

		private void log(Consumer<String> logger, String... reports) {
			if (reports == null || reports.length == 0) {
				logger.accept("\n");
				return;
			}
			for (String report : reports) {
				logger.accept(report + "\n");
			}
		}


		private static class WindowHandler extends Handler {
		    private StyledDocument console;
		    private final static SimpleAttributeSet debugTextStyle;
		    private final static SimpleAttributeSet infoTextStyle;
		    private final static SimpleAttributeSet warnTextStyle;
		    private final static SimpleAttributeSet errorTextStyle;
			private final static int maxNumberOfCharacters = Integer.valueOf(EnvironmentUtils.getVariable("logger.window.max-number-of-characters", "25165824"));
			private final static String backgroundColor = EnvironmentUtils.getVariable("logger.window.background-color", "67,159,54");
			private final static String textColor = EnvironmentUtils.getVariable("logger.window.text-color", "253,195,17");
			private final static String barBackgroundColor = EnvironmentUtils.getVariable("logger.window.bar.background-color", "253,195,17");
			private final static String barTextColor = EnvironmentUtils.getVariable("logger.window.bar.text-color", "67,159,54");

			static {
				com.formdev.flatlaf.FlatLightLaf.setup();
				JFrame.setDefaultLookAndFeelDecorated(true);
				debugTextStyle = new SimpleAttributeSet();
			    StyleConstants.setForeground(debugTextStyle, Color.BLUE);
			    //StyleConstants.setBackground(debugTextStyle, Color.YELLOW);

			    infoTextStyle = new SimpleAttributeSet();
			    StyleConstants.setForeground(infoTextStyle, Color.WHITE);

			    warnTextStyle = new SimpleAttributeSet();
			    StyleConstants.setForeground(warnTextStyle, Color.YELLOW);
			    StyleConstants.setBold(warnTextStyle, true);

			    errorTextStyle = new SimpleAttributeSet();
			    StyleConstants.setForeground(errorTextStyle, Color.RED);
			    StyleConstants.setBold(errorTextStyle, true);
			}

			private WindowHandler() {
				//LogManager manager = LogManager.getLogManager();
				//String className = this.getClass().getName();
				//String level = manager.getProperty(className + ".level");
				//setLevel(level != null ? Level.parse(level) : Level.ALL);
				setLevel(Level.ALL);
				if (console == null) {						javax.swing.JFrame window = new javax.swing.JFrame(EnvironmentUtils.getVariable("lottery.application.name", "Event logger")) {
						private static final long serialVersionUID = 653831741693111851L;
						{
							setSize(1024, 768);
						}
					};
					JTextPane textpane = new JTextPane();
					console = textpane.getStyledDocument();					javax.swing.text.DefaultCaret caret = (javax.swing.text.DefaultCaret)textpane.getCaret();
					caret.setUpdatePolicy(javax.swing.text.DefaultCaret.ALWAYS_UPDATE);
					textpane.setBorder(
						BorderFactory.createCompoundBorder(
							textpane.getBorder(),
							BorderFactory.createEmptyBorder(5, 5, 5, 5)
						)
					);

					textpane.setBackground(stringToColor(WindowHandler.backgroundColor));
					textpane.setForeground(stringToColor(WindowHandler.textColor));

					textpane.setFont(new Font(textpane.getFont().getName(), Font.PLAIN, textpane.getFont().getSize() + 2));

					JPanel noWrapPanel = new JPanel(new BorderLayout());
					noWrapPanel.add(textpane);
					JScrollPane scrollPane = new javax.swing.JScrollPane(noWrapPanel);
					window.add(scrollPane);
					window.getRootPane().putClientProperty("JRootPane.titleBarForeground", stringToColor(WindowHandler.barTextColor));
					window.getRootPane().putClientProperty("JRootPane.titleBarBackground", stringToColor(WindowHandler.barBackgroundColor));

					scrollPane.getHorizontalScrollBar().setBackground(stringToColor(WindowHandler.barBackgroundColor));
					scrollPane.getVerticalScrollBar().setBackground(stringToColor(WindowHandler.barBackgroundColor));

					window.setVisible(true);
					window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				}
			}

			protected Color stringToColor(String colorAsString) {
				List<Integer> rGBColor =
					Arrays.asList(colorAsString.split(",")).stream()
					.map(Integer::valueOf).collect(Collectors.toList());
				if (rGBColor.size() == 3) {
					return new Color(rGBColor.get(0), rGBColor.get(1), rGBColor.get(2));
				} else if (rGBColor.size() == 4) {
					return new Color(rGBColor.get(0), rGBColor.get(1), rGBColor.get(2), rGBColor.get(3));
				} else {
					throw new IllegalArgumentException("Unvalid color " + colorAsString);
				}
			}

			public static Logger attachNewWindowToLogger(String loggerName) {
				WindowHandler WindowHandler = new WindowHandler();
				Logger logger = Logger.getLogger(loggerName);
				logger.addHandler(WindowHandler);
				return logger;
			}

			@Override
			public synchronized void publish(LogRecord record) {
				if (!isLoggable(record)) {
					return;
				}
				try {
					if (console.getEndPosition().getOffset() > maxNumberOfCharacters) {
						console.remove(0, maxNumberOfCharacters - 1);
					}
					console.insertString(
						console.getEndPosition().getOffset() -1,
						record.getMessage(),
						getSimpleAttributeSet(record.getLevel())
					);
				} catch (BadLocationException exc) {

				}
			}

			private AttributeSet getSimpleAttributeSet(Level level) {
				if (level == Level.FINE) {
					return debugTextStyle;
				} else if (level == Level.INFO) {
					return infoTextStyle;
				} else if (level == Level.WARNING) {
					return warnTextStyle;
				} else if (level == Level.SEVERE) {
					return errorTextStyle;
				}
				return null;
			}

			@Override
			public void close() {}

			@Override
			public void flush() {}

		}

	}

}
