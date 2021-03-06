/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package com.borwe.JSouthParkDownlader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

public class App {
	private static final String version="0.01";

    public static void main(String[] args) throws IOException {
    	
    	//check that second argument passed for directory to use
    	if(args.length<=0) {
    		System.out.println("You did not pass a directory to download from");
    		System.out.println("Please do java -jar JSouthParkDownlader.jar <DIRECTORY TO DOWNLOAD TO>");
    		System.out.println("Retry and thanks");
    		return ;
    	}
    	String directoryToDownload=args[0];
    	//check that directoryToDownload == "help"
    	if(directoryToDownload.equals("help")) {
    		//show help information
    		System.out.println("java -jar JSouthParkDownlader.jar <DIRECTORY TO DOWNLOAD TO>\t to download");
    		System.out.println("java -jar JSouthParkDownlader.jar help\t to show this menu");
    		System.out.println("java -jar JSouthParkDownlader.jar version\t to see version of the application");
    		return;
    	}
    	//if directoryToDownload == "version"
    	if(directoryToDownload.equals("version")) {
    		//show version of application
    		System.out.println("Version of JSouthParkDownlader is: "+version);
    		return;
    	}
    	//check if directoryDownload is valid directory or not
    	if(!new File(directoryToDownload).exists()) {
    		System.out.println("The directory passed does not exit");
    		return;
    	}
    	System.out.println("DIRECTORY DOWNLOAD: "+args[0]);
    	System.out.println("STARTING!");
    	
    	VideoScrapper scrapper=new VideoScrapper(directoryToDownload);
    	scrapper.startScrappForSouthParkVideoLinks();
    	scrapper.startDownloads();
    }
}

/**
 * Used for 
 * @author brian
 *
 */
class VideoData{
	@Getter @Setter
	String website;
	@Getter @Setter
	String file;
	
	public boolean matches(VideoData obj) {
		if(obj.getWebsite().contentEquals((this.getWebsite())) 
				&& obj.getFile().contentEquals(this.getFile())) {
			return true;
		}
		return false;
	}
}

@ToString
class VideoScrapper{
	
	@Getter @Setter
	private List<VideoData> seriesAndLinks;
	
	@Getter
	private List<VideoData> downloadedLinks;
	
	//used for storing file for where to store download info
	@Getter @Setter
	private String fileLocation;
	
	@Getter
	private static final String mainfileNameForData="data_downloads.txt";
	
	@Getter
	private static final String mainLink="http://sp.lolfile.com/episodes/";
	
	public VideoScrapper(String locationOfDownload) throws JsonParseException, JsonMappingException, IOException{
		this.fileLocation=locationOfDownload;
		seriesAndLinks=new ArrayList<VideoData>();
		
		downloadedLinks=new ArrayList<VideoData>();
		
		File listFile=new File(locationOfDownload);
		//check if file exists
		if(listFile.exists()) {
			//if exists then go ahead and try to read from it placind data in downloadedLinks
			File fileToRead=new File(listFile.getAbsolutePath()+File.separatorChar+mainfileNameForData);
			System.out.println("READING DOWNLOADS FROM: "+fileToRead.getAbsolutePath());
			if(fileToRead.exists()) {
				ObjectMapper mapper=new ObjectMapper();
				downloadedLinks=mapper.readValue(fileToRead, new TypeReference<List<VideoData>>() {
				});
			}
		}
	}
	
	public void startScrappForSouthParkVideoLinks() throws IOException {
		System.out.println("STARTING: scrapping for website links");
		Response response=Jsoup.connect(this.getMainLink()).execute();
		
		Document document=response.parse();
		var linkElements=document.getElementsByTag("a");
		
		//for holding links of series pages
		List<String> seriesPages=new ArrayList<String>();
		
		linkElements.forEach(element->{
			/*
			 * now get the href link, and add them to the end of mainlink,
			 * if they don't contain http:// or https:// in the value
			 * Must also contain an /S
			 */
			String hrefAttribute=element.attr("href");
			if(hrefAttribute!=null && hrefAttribute.contains("S")) {
				//to hold the value of the series link page
				String seriesLink=this.getLink(this.getMainLink(), hrefAttribute);
				seriesPages.add(seriesLink);
			}
		});
		
		seriesAndLinks=Observable.fromIterable(seriesPages).observeOn(Schedulers.computation())
			.map(sPage->{
				return scrapItEpisodes(sPage);
			}).map(episodesPage->{
				return getEpisodeLink(episodesPage);
			}).flatMap(videoDataLists->{
				return Observable.fromIterable(videoDataLists);
			}).toList().blockingGet();
	}
	
	/**
	 * Start the process of downloading the files
	 */
	public void startDownloads() {
		Observable.fromIterable(seriesAndLinks)
			.filter(videoData->{
				//filter to only remain with those not downloaded yet
				return fileDownloaded(videoData)==false;
			}).map(videoData->{
				//now begin the download here
				System.out.println("DOWNLOADING: "+videoData.getWebsite());
				boolean success=this.downloadFile(videoData);
				if(success=true) {
					//meaning if succesfully downloaded
					//then go ahead and append th mainFilenameData with this info
					promptDownloadSuccess(success, videoData);
					updateDownloadsList(videoData);
				}else {
					//else means we failed to download, prompt user
					promptDownloadSuccess(success, videoData);
				}
				return videoData;
			}).blockingSubscribe();
	}
	
	/**
	 * Used for updating the data, syncronised since called from many threads
	 * @param data
	 * @throws IOException 
	 */
	private synchronized void updateDownloadsList(VideoData data) throws IOException {
		this.downloadedLinks.add(data);
		//store file location
		File fileToWrite=new File(this.getFileLocation()
				+File.separatorChar+this.mainfileNameForData);
		//delete file if exists
		if(fileToWrite.exists()) {
			fileToWrite.delete();
		}
		ObjectMapper mapper=new ObjectMapper();
		String whatToWrite=mapper.writeValueAsString(this.getDownloadedLinks());
		//now write it to the fileToWrite
		FileWriter writer=new FileWriter(fileToWrite);
		writer.write(whatToWrite);
		writer.flush();
		writer.close();
	}
	
	/**
	 * Used for showing prompts/alerts on terminal for the user
	 * synchronised since called from many threads
	 * @param success
	 * @param data
	 */
	private synchronized void promptDownloadSuccess(boolean success, VideoData data) {
		if(success=false) {
			System.err.println("ERROR AT: "+data.getWebsite()+" during download");
		}else {
			
			System.out.println("SUCCESS AT: "+data.getWebsite()+" during download");
		}
	}
	
	/**
	 * Used for downloading a file,
	 * returns true if file succesfully downloads, false otherwise
	 * @param data
	 * @return
	 * @throws IOException 
	 * @throws MalformedURLException 
	 */
	private boolean downloadFile(VideoData data) throws MalformedURLException, IOException {
		//to hold the bytes downloaded
		long downloadBytes=0;
		//the webstream for downloading
		InputStream webStream=new URL(data.getWebsite()).openStream();
		//the file stream to save to
		FileOutputStream fileStream=new FileOutputStream(this.getFileLocation()+File.separatorChar+data.getFile());
		downloadBytes=IOUtils.copyLarge(webStream, fileStream);
		
		//if downloadBytes > 0 meaning download was started and possibly completed.
		if(downloadBytes>0) {
			return true;
		}
		return false;
	}
	
	/**
	 * Checks if a file was downloaded before, or not
	 * if so return true, otherwise return false
	 * @param videoData
	 * @return
	 */
	public boolean fileDownloaded(VideoData videoData) {
		for(int i=0;i<downloadedLinks.size();++i) {
			if(downloadedLinks.get(i).matches(videoData)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Used for scrapping Episodes off a series page, providing links that are to be
	 * used for downloading and saving
	 * @param seriesPage
	 * @return episodeLink
	 * @throws IOException 
	 */
	private Document scrapItEpisodes(String seriesPage) throws IOException {
		
		try{
			Document pageDoc=Jsoup.connect(seriesPage).execute().parse();
			return pageDoc;
		}catch(Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}
	
	/**
	 * For parsing all episodes with their links, from a given series
	 * @param EpisodesPage
	 * @return
	 */
	private List<VideoData> getEpisodeLink(Document episodesPage) {
		List<VideoData> episodesLink=new ArrayList<>();
		String URL=episodesPage.baseUri();
		
		
		//get episodes URL
		episodesPage.getElementsByTag("a").forEach(element->{
			String link=element.attr("href");
			String file="";
			
			if(checkIfLinkNotBackAndContainsNumbers(link)) {
				//we reach here, means that it is a valid link, not a back link
				link=VideoScrapper.this.getLink(URL, link)+"movie/";
				try {
					file=parseFirstAForMedia(link);
					link=link+file;
					VideoData video=new VideoData();
					video.setWebsite(link);
					video.setFile(file);
					episodesLink.add(video);
					
//					System.out.println("KKK: "+file);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
//			System.out.println("JJJJJJ: "+link);
			
		});
		return episodesLink;
	}
	
	/**
	 * Used to parse a link for first <a> tag for media files on a specific page
	 * @param link
	 * @return
	 * @throws IOException
	 */
	private String parseFirstAForMedia(String link) throws IOException {
		var page= Jsoup.connect(link).execute().parse();
		
		
		class MutableString{
			@Getter @Setter
			String value="";
		};
		MutableString mediaLinkHolder=new MutableString();
		
		//get first A link
		page.getElementsByTag("a").forEach(element->{
			if(mediaLinkHolder.getValue().isEmpty()==true && checkIfLinkNotBack(element.attr("href"))) {
				//return the link
				mediaLinkHolder.setValue(element.attr("href"));
			}
		}); 
		
		
		return mediaLinkHolder.getValue();
	}
	
	/**
	 * Check that link isn't a back dir route ../
	 * @param link
	 * @return
	 */
	private boolean checkIfLinkNotBack(String link) {
		//return false if link is empty
		if(link==null || link.isEmpty()) {
			return false;
		}
		//check if not directory link
		if(link.contains("..") ) {
			return false;
		}
		return true;
	}
	
	/**
	 * Check that link actually valid, doesn't contain any
	 * back directories, or current directories
	 * @return
	 */
	private boolean checkIfLinkNotBackAndContainsNumbers(String link) {
		if(checkIfLinkNotBack(link)==false) {
			return false;
		}
		
		//validate that it actually is a number or starts with a number
		char first=link.charAt(0);
		if(Character.isDigit(first)==false) {
			return false;
		}
		
		return true;
	}
	
	/**
	 * Parses a parsed in link to check if full or not,
	 * if not goes ahead and adds the beginning towards it
	 * @param link, beginning
	 * @return
	 */
	private String getLink(String beginning,String link) {
		String finalLink="";
		
		if(link.contains("http")) {
			finalLink=link;
		}else {
			finalLink=beginning+link;
		}
		
		return finalLink;
	}
}