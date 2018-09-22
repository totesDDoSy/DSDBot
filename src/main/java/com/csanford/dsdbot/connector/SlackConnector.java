package com.csanford.dsdbot.connector;

import com.csanford.dsdbot.MessageHistory;
import com.csanford.dsdbot.constants.Constants;
import com.csanford.dsdbot.constants.SecureConstants;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.SlackUser;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import com.ullink.slack.simpleslackapi.listeners.ReactionAddedListener;
import com.ullink.slack.simpleslackapi.listeners.SlackMessageDeletedListener;
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener;
import com.ullink.slack.simpleslackapi.listeners.SlackMessageUpdatedListener;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Message;

/**
 * Provides an encapsulation of Slack listeners.
 * @author csanford
 * @date Sep 22, 2018
 */
public class SlackConnector
{

	private final SlackSession slackSession;
	private final JDA jda;
	private final MessageHistory messageHistory;

	/**
	 * Create the Slack to Discord connector.
	 * @param jda The Discord instance we'll be talking to.
	 * @param messageHistory A message history to keep track of messages sent.
	 */
	public SlackConnector( JDA jda, MessageHistory messageHistory )
	{
		this.slackSession = SlackSessionFactory.getSlackSessionBuilder( SecureConstants.SLACK_TOKEN ).build();;
		this.jda = jda;
		this.messageHistory = messageHistory;
	}

	/**
	 * Add all the listeners to the slack session.
	 */
	public void addListeners()
	{
		addMessagePostedListener();
		addMessageUpdatedListener();
		addMessageDeletedListener();
		addReactionAddedListener();
	}
	
	/**
	 * Connect the Slack session.
	 * @throws IOException 
	 */
	public void connect() throws IOException
	{
		slackSession.connect();
	}
	
	/**
	 * Getter for the Slack session.
	 * @return The Slack session.
	 */
	public SlackSession getSlackSession()
	{
		return this.slackSession;
	}

	/**
	 * Adds the message posted listener to the Slack session.
	 * Currently takes the Slack message, translates it a bit, then posts it to
	 * Discord and saves the message to the history.
	 */
	private void addMessagePostedListener()
	{
		SlackMessagePostedListener slackMessagePostedListener = ( event, session ) ->
		{
			// Slack Message Listener
			String slackMessage = event.getMessageContent();
			String selfId = session.sessionPersona().getId();
			SlackUser sender = event.getSender();
			if ( !session.sessionPersona().getId().equals( sender.getId() )
					&& slackMessage.contains( "@" + selfId ) )
			{
				// If the bot didn't send the message and was mentioned, send a discord message
				String discordMessage = convertSlackMessage( slackMessage, sender.getUserName(), session );

				// Send the discord message
				Message message = jda.getTextChannelsByName( Constants.DISCORD_CHANNEL, true ).get( 0 )
						.sendMessage( discordMessage ).complete();

				messageHistory.saveDiscordMessage( event.getTimestamp(), message );
			}
		};

		slackSession.addMessagePostedListener( slackMessagePostedListener );
	}

	/**
	 * Adds the message updated listener to the Slack session.
	 * Currently finds the message in the history and updates it, updating the
	 * message in Discord.
	 */
	private void addMessageUpdatedListener()
	{
		SlackMessageUpdatedListener slackMessageUpdatedListener = ( event, session ) ->
		{
			Message message = messageHistory.getDiscordMessage( event.getMessageTimestamp() );
			if ( message != null )
			{
				String discordMsg = message.getContentDisplay();
				StringBuilder slackMessage = new StringBuilder();
				slackMessage.append( discordMsg.subSequence( 0, discordMsg.indexOf( ' ' ) + 1 ) );
				slackMessage.append( convertSlackMessage( event.getNewMessage(), null, session ) );

				Message newMessage = message.editMessage( slackMessage.toString() ).complete();
				// do stuff with history?
			}
		};

		slackSession.addMessageUpdatedListener( slackMessageUpdatedListener );
	}

	/**
	 * Adds the message delted listener to the Slack session.
	 * Currently just finds the message in the history and deletes it, along with
	 * deleting it from Discord.
	 */
	private void addMessageDeletedListener()
	{
		SlackMessageDeletedListener slackMessageDeletedListener = ( event, session ) ->
		{
			// Message deleted on slack
			Message discordMsg = messageHistory.removeDiscordMessage( event.getMessageTimestamp() );
			if ( discordMsg != null )
			{
				discordMsg.delete().complete();
			}
		};

		slackSession.addMessageDeletedListener( slackMessageDeletedListener );
	}

	/**
	 * Adds the reaction added listener to the Slack session.
	 * Currently doesn't work :(
	 */
	private void addReactionAddedListener()
	{
		ReactionAddedListener slackReactionAddedListener = ( event, session ) ->
		{
			Message message = messageHistory.getDiscordMessage( event.getMessageID() );
			if ( message != null )
			{
				System.out.println( event.getEmojiName() );
				message.addReaction( event.getEmojiName() ).complete();
			}
		};

		slackSession.addReactionAddedListener( slackReactionAddedListener );
	}

	/**
	 * Converts a Slack message to Discord message style.
	 * @param slackMessage The slack message sent.
	 * @param sender The sender of the message OR null if the sender is unknown.
	 * @param session The Slack session, used for translation of names.
	 * @return 
	 */
	private static String convertSlackMessage( String slackMessage, String sender, SlackSession session )
	{
		StringBuilder discordMessage = new StringBuilder();

		// Make a map for user IDs to names
		Map<String, String> usersMap = session.getUsers().stream()
				.collect( Collectors.toMap( user -> user.getId(), user -> user.getUserName() ) );
		for ( Map.Entry<String, String> entry : usersMap.entrySet() )
		{
			slackMessage = slackMessage
					.replaceAll( entry.getKey(), entry.getValue() )
					.replaceAll( "[<>]", "" );
		}

		if ( sender != null )
		{
			discordMessage.append( "**" ).append( sender ).append( "**: " );
		}

		discordMessage.append( slackMessage );

		return discordMessage.toString();
	}
}