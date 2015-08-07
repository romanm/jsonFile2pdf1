package hello;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {
	private static final Logger logger = LoggerFactory.getLogger(ScheduledTasks.class);
	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
	private static String workDir = "/home/roman/jura/workshop-manuals1991/";
	//develop
	private static String dirJsonName = workDir + "OUT1json/";
	private static String dirPdfName = workDir+ "PDF/";
	private static String dirLargeHtmlName = workDir+ "HTMLLONG/";
	DateTime startMillis;
	static PeriodFormatter hmsFormatter = new PeriodFormatterBuilder()
			.appendHours().appendSuffix("h ")
			.appendMinutes().appendSuffix("m ")
			.appendSeconds().appendSuffix("s ")
			.toFormatter();
	final static Path dirJsonStart = Paths.get(dirJsonName);
	private	int fileIdx = 0;
	int filesCount;

	@Scheduled(fixedRate = 500000000)
	public void reportCurrentTime() {
		startMillis = new DateTime();
		System.out.println("The time is now " + dateFormat.format(startMillis.toDate()));
		filesCount = countFiles2(dirJsonStart.toFile());
		logger.debug("Files count " + filesCount + ". The time is now " + dateFormat.format(new Date()));
		logger.debug(dirJsonName);
		try {
			makeLargeHTML();
//			makePdfFromHTML();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void makeLargeHTML() throws IOException {
		logger.debug("Start folder : "+dirJsonStart);
		Files.walkFileTree(dirJsonStart, new SimpleFileVisitor<Path>() {
			
			@Override
			public FileVisitResult visitFile(Path file,
					BasicFileAttributes attrs) throws IOException {
				final FileVisitResult visitFile = super.visitFile(file, attrs);
				fileIdx++;
				logger.debug(fileIdx + "" + "/" + filesCount +  procentWorkTime() + file);
				return visitFile;
			}

		});

	}
	String procentWorkTime() {
		int procent = fileIdx*100/filesCount;
		String workTime = hmsFormatter.print(new Period(startMillis, new DateTime()));
		String procentSecond = " - html2pdf3 - (" + procent + "%, " + workTime + "s)";
		return procentSecond;
	}
	public static int countFiles2(File directory) {
		int count = 0;
		for (File file : directory.listFiles()) {
			if (file.isDirectory()) {
				count += countFiles2(file); 
			}else
				count++;
		}
		return count;
	}

}
