package com.csanford.dsdbot;

import com.csanford.dsdbot.constants.Constants;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.dv8tion.jda.core.entities.Message;

/**
 * Message history is a class used to keep track of the messages sent
 * from both Slack to Discord and Discord to Slack.
 * @author csanford
 * #date Sep 22, 2018
 */
public class MessageHistory
{
	private static Map< String, Message> stodMessages;
	private static Queue< String> stodHistory;
	private static Map< Long, String> dtosMessages;
	private static Queue< Long> dtosHistory;
	
	public MessageHistory()
	{
		stodMessages = new HashMap<>();
		stodHistory = new ConcurrentLinkedQueue<>();
		dtosMessages = new HashMap<>();
		dtosHistory = new ConcurrentLinkedQueue<>();
	}

	/**
	 * Removes a message sent from Discord to Slack from history. Returns the Slack
	 * timestamp of the removed message or null if one couldn't be found.
	 * @param messageID The Discord message ID associated with the Slack message.
	 * @return The timestamp of the Slack message or null if one could not be found.
	 */
	public String removeSlackMessage( Long messageID )
	{
		dtosHistory.remove( messageID );
		return dtosMessages.remove( messageID );
	}
	
	/**
	 * Removes a message sent from Slack to Discord from the history. Returns the
	 * Discord message or null if one could not be found.
	 * @param timestamp The Slack timestamp associated with the Discord message.
	 * @return The Discord message or null of one could not be found.
	 */
	public Message removeDiscordMessage( String timestamp )
	{
		stodHistory.remove( timestamp );
		return stodMessages.remove( timestamp );
	}
	
	/**
	 * Retrieve the Slack timestamp associated with a Discord messageID.
	 * @param messageID A Discord message ID.
	 * @return A timestamp for a Slack message or null if one could not be found.
	 */
	public String getSlackTimestamp( Long messageID )
	{
		return dtosMessages.get( messageID );
	}
	
	/**
	 * Retrieve the Discord message associated with a Slack timestamp.
	 * @param timestamp A Slack timestamp.
	 * @return A message for a Slack timestamp or null if one could not be found.
	 */
	public Message getDiscordMessage( String timestamp )
	{
		return stodMessages.get( timestamp );
	}
	
	/**
	 * Save a discord message with a slack timestamp.
	 *
	 * @param timestamp Timestamp of slack message.
	 * @param message Discord message object.
	 */
	public void saveDiscordMessage( String timestamp, Message message )
	{
		stodHistory.add( timestamp );
		if ( stodHistory.size() > Constants.MAX_MSG_HISTORY - 1 )
		{
			// Log this probably
			stodMessages.remove( stodHistory.remove() );
		}
		stodMessages.put( timestamp, message );
	}

	/**
	 * Save a slack message with a discord message long id.
	 *
	 * @param messageId The Discord long message id.
	 * @param timestamp Timestamp of slack message.
	 */
	public void saveSlackMessage( Long messageId, String timestamp )
	{
		dtosHistory.add( messageId );
		if ( dtosHistory.size() > Constants.MAX_MSG_HISTORY - 1 )
		{
			dtosMessages.remove( dtosHistory.remove() );
		}
		dtosMessages.put( messageId, timestamp );
	}
}
