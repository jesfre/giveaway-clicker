package com.blogspot.jesfre.giveawayclicker;

import java.io.File;
import java.util.List;
import java.util.NoSuchElementException;

import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;


/**
 * @author <a href="mailto:jorge.ruiz.aquino@gmail.com">Jorge Ruiz Aquino</a>
 * Sep 20, 2018
 */
public class Watcher {
	private static final long DEFAULT_SLEEP = 10_000;
	private static final int GRID_ITEM_SIZE = 24;
	private static final String GIVEAWAYS_URL = "https://www.amazon.com/ga/giveaways";
	private static final String GIVEAWAYS_PAGEID_URL = GIVEAWAYS_URL + "?pageId=";
	
	private static final String AM_USER_NAME = "username or email";
	private static final String AM_PASSWORD = "password";

	public static void main(String[] args) throws Exception {
		// TODO add DB capability to store visited and prevent revisiting
		// TODO add notification feature
		int startFromItem = 1;
		int startFromPage = 2;

		Watcher watcher = new Watcher();
		watcher.init();
		watcher.login(AM_USER_NAME, AM_PASSWORD);
		System.out.println("Loading " + GIVEAWAYS_PAGEID_URL + startFromPage);
		watcher.load(GIVEAWAYS_PAGEID_URL + startFromPage);
		watcher.work(startFromPage, startFromItem, 0);
		watcher.close();
	}


	private WebDriver driver = null;
	private int currentPage = 0;

	private void init() {
		initChrome();
	}

	private void initChrome() {
		ChromeOptions options = new ChromeOptions();
		options.addArguments("disable-infobars");
		options.addArguments("--start-maximized");
		System.setProperty("webdriver.chrome.driver", "D:/dev/selenium-web-drivers/chromedriver.exe");
		driver = new ChromeDriver(options);
	}

	private void load(String url) {
		driver.get(url);
	}

	private void login(String usr, String pwd) {
		load("https://www.amazon.com");

		WebElement accountListing = driver.findElement(By.id("nav-link-accountList"));
		accountListing.click();

		if(!driver.getTitle().contains("Sign In")) {
			System.out.println("Already signed-in.");
			return;
		}

		WebElement emailInput = driver.findElement(By.id("ap_email"));
		emailInput.sendKeys(usr);
		sleep(2);
		emailInput.submit();

		WebElement pwdInput = driver.findElement(By.id("ap_password"));
		pwdInput.sendKeys(pwd);
		sleep(2);
		pwdInput.submit();
	}

	/**
	 * @param startFromPage pagination page number
	 * @param startFrom any value < 1 means to start from 1
	 * @param workItemsCount the amount of items to enter, 0 for unlimited
	 */
	private void work(int startFromPage, int startFrom, int workItemsCount) {
		currentPage = startFromPage;
		WebElement itemContainter = driver.findElement(By.id("giveaway-grid-container"));
		List<WebElement> itemContainterList = itemContainter.findElements(By.className("giveawayItemContainer"));
		int itemSize = itemContainterList.size();

		if(startFrom < 1) { startFrom = 1; }

		boolean unlimited = workItemsCount <= 0;
		workItemsCount += startFrom;

		if(startFrom > itemSize) {
			System.err.println("Not enough items to start. Start:" +startFrom + ", Item size:"+itemSize);
			return;
		}
		if(itemSize < GRID_ITEM_SIZE) {
			// Is the last page
			workItemsCount = itemSize;
		}

		for(int count = startFrom; unlimited || count <= workItemsCount; count++) {
			if(count > itemSize) {
				// Continue in the next page
				workItemsCount -= (count - 1);
				count = 1;
				currentPage++;
				System.out.println("Moving to page " + currentPage);
				load(GIVEAWAYS_PAGEID_URL + currentPage);
			}

			WebElement item = driver.findElement(By.id("giveaway-item-" + count));
			String detailsText = null;
			try {
				WebElement detailsTextWe = item.findElement(By.className("a-size-base"));
				detailsText = detailsTextWe.getText();

				WebElement detailsLink = item.findElement(By.className("giveAwayItemDetails"));
				detailsLink.click();
				sleep(2);

				System.out.print("Item " + count + " | ");
				String finalStatus = workItem();
				System.out.println("Status: " + finalStatus);
			} catch (Exception e) {
				System.err.println("Failed page #"+currentPage+", item #"+count+": " + detailsText);
				e.printStackTrace();
			}

			load(GIVEAWAYS_PAGEID_URL + currentPage);
			sleep(2);
			System.out.println("..............................\n");
		}
	}

	private String workItem() {
		String status = "Processing";

		boolean isEnded = driver.getPageSource().contains("Giveaway ended");
		WebElement prizeContainer = driver.findElement(By.id("giveaway-prize-container"));
		boolean visited = false;
		if(isEnded) {
			status = "Ended!!!";
		} else {
			visited = driver.getPageSource().contains("you didn't win");
			if(visited) {
				status = "Visited!!!";
			}
		}

		WebElement prizeNameWe = prizeContainer.findElement(By.id("prize-name"));
		String prizeName = prizeNameWe.getAttribute("title");

		WebElement prizeCostContainerWe = prizeContainer.findElement(By.className("qa-prize-cost-container"));
		WebElement prizeCostWe = prizeCostContainerWe.findElement(By.className("a-size-large"));
		String prizecost = prizeCostWe.getText();

		System.out.println(status + ": " + prizeName);
		if(isEnded || visited) {
			return status;
		}

		System.out.println("\tCost: " + prizecost);
		String type = null;
		boolean entered = false;
		try {
			System.out.print("\tType: " );
			type = "Box";
			try {
				WebElement boxWe = driver.findElement(By.id("box_click_target"));
				System.out.println(" Found: " + type);
				//				WebDriverWait wait = new WebDriverWait(driver, 30);
				//				WebElement boxWeClickable = wait.until(ExpectedConditions.elementToBeClickable(By.id("box_click_target")));
				boxWe.click();
				entered = true;
			} catch(NoSuchElementException e2) {
				System.err.print("\tNot> " + type);
				type = "Youtube";
				try {
					WebElement iframe = driver.findElement(By.id("youtube-container"));
					System.out.println(". Found: " + type);

					driver.switchTo().frame("youtube-iframe");
					WebElement videoWe = driver.findElement(By.id("player"));
					videoWe.click();
					driver.switchTo().defaultContent();

					WebDriverWait wait = new WebDriverWait(driver, 30);
					WebElement continueWe = wait.until(ExpectedConditions.elementToBeClickable(By.name("continue")));
					continueWe.click();
					entered = true;
				} catch(NoSuchElementException e3) {
					System.err.print(", " + type);
					type = "Video Airy";
					try {
						WebElement videoWe = driver.findElement(By.className("airy-play-toggle-hint-stage"));
						System.out.println(". Found: " + type);
						videoWe.click();

						WebDriverWait wait = new WebDriverWait(driver, 30);
						WebElement continueWe = wait.until(ExpectedConditions.elementToBeClickable(By.name("continue")));
						continueWe.click();
						entered = true;
					} catch(NoSuchElementException e4) {
						System.err.print(", " + type);
						type ="Follow";
						try {
							WebElement folowWe = driver.findElement(By.id("follow-connect-url"));
							System.out.println(". Found: " + type);
							folowWe.click();
							entered = true;
						} catch(NoSuchElementException e1) {
							System.err.print(", " + type);
							System.err.println(". Cannot find type!!!");
						}
					}
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		if(entered) {
			sleep(10);
			printResults();
			return "Processed";
		} else {
			sleep(1);
			return "Not entered";
		}
	}

	private void printResults() {
		try {
			WebDriverWait wait = new WebDriverWait(driver, 30);
			WebElement resultWe = wait.until(ExpectedConditions.presenceOfElementLocated(By.className("prize-header-container")));
			String result = resultWe.getText();
			System.out.println("Result: " + result);
		} catch(Exception e) {
			System.err.println("Cannot determine the result.");
			// Print content
			WebElement pageContent = driver.findElement(By.id("giveaway-page-content"));
			System.err.println("Page Content text: ");
			System.err.println(pageContent.getText());
			// Save screenshot
			File scrFile = ((TakesScreenshot)(driver)).getScreenshotAs(OutputType.FILE);
			System.out.println(scrFile.getAbsolutePath());
			e.printStackTrace();
		}
	}

	private void close() {
		driver.quit();
	}

	private static void sleep(int seconds) {
		long secondsL = seconds * 1000;
		if(secondsL < 1) { secondsL = DEFAULT_SLEEP; }
		try {
			Thread.sleep(secondsL);
		} catch (InterruptedException e) {
			e.printStackTrace();
		};
	}


}
