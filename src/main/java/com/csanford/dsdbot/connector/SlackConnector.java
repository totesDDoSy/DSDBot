package com.csanford.dsdbot.connector;

import com.csanford.dsdbot.MessageHistory;
import com.csanford.dsdbot.constants.Constants;
import com.csanford.dsdbot.constants.SecureConstants;
import com.csanford.dsdbot.constants.UserBiMap;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.SlackUser;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import com.ullink.slack.simpleslackapi.listeners.ReactionAddedListener;
import com.ullink.slack.simpleslackapi.listeners.ReactionRemovedListener;
import com.ullink.slack.simpleslackapi.listeners.SlackMessageDeletedListener;
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener;
import com.ullink.slack.simpleslackapi.listeners.SlackMessageUpdatedListener;
import com.vdurmont.emoji.Emoji;
import com.vdurmont.emoji.EmojiManager;
import java.io.IOException;
import java.util.Arrays;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageReaction;
import net.dv8tion.jda.core.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides an encapsulation of Slack listeners.
 *
 * @author csanford
 * #date Sep 22, 2018
 */
public class SlackConnector
{

	private static final Logger LOG = LoggerFactory.getLogger( SlackConnector.class );

	private final SlackSession slackSession;
	private final JDA jda;
	private final MessageHistory messageHistory;

	/**
	 * Create the Slack to Discord connector.
	 *
	 * @param jda The Discord instance we'll be talking to.
	 * @param messageHistory A message history to keep track of messages sent.
	 */
	public SlackConnector( JDA jda, MessageHistory messageHistory )
	{
		LOG.info( "Creating Slack session" );
		this.slackSession = SlackSessionFactory.getSlackSessionBuilder( SecureConstants.SLACK_TOKEN ).build();;
		this.jda = jda;
		this.messageHistory = messageHistory;
	}

	/**
	 * Add all the listeners to the slack session.
	 */
	public void addListeners()
	{
		LOG.info( "Binding Slack listeners" );
		addMessagePostedListener();
		addMessageUpdatedListener();
		addMessageDeletedListener();
		addReactionAddedListener();
		addReactionRemovedListener();
	}

	/**
	 * Connect the Slack session.
	 *
	 * @throws IOException Error Connecting.
	 */
	public void connect() throws IOException
	{
		slackSession.connect();
	}

	/**
	 * Getter for the Slack session.
	 *
	 * @return The Slack session.
	 */
	public SlackSession getSlackSession()
	{
		return this.slackSession;
	}

	/**
	 * Adds the message posted listener to the Slack session. Currently takes
	 * the Slack message, translates it a bit, then posts it to Discord and
	 * saves the message to the history.
	 */
	private void addMessagePostedListener()
	{
		SlackMessagePostedListener slackMessagePostedListener = ( event, session ) ->
		{
			// Slack Message Listener
			String slackMessage = event.getMessageContent();
			String selfId = session.sessionPersona().getId();
			SlackUser sender = event.getSender();
			String timestamp = event.getTimestamp();
			if ( !session.sessionPersona().getId().equals( sender.getId() )
					&& slackMessage.contains( "@" + selfId ) )
			{
				LOG.debug( "Slack message posted from " + sender.getUserName() );
				// If the bot didn't send the message and was mentioned, send a discord message
				Message discordMessage = convertSlackMessage( slackMessage, sender );
				// Send the discord message
				jda.getTextChannelsByName( Constants.DISCORD_CHANNEL, true ).get( 0 )
						.sendMessage( discordMessage )
						.queue( message -> messageHistory.saveDiscordMessage( timestamp, message ) );
			}
		};

		slackSession.addMessagePostedListener( slackMessagePostedListener );
	}

	/**
	 * Adds the message updated listener to the Slack session. Currently finds
	 * the message in the history and updates it, updating the message in
	 * Discord.
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
				slackMessage.append( event.getNewMessage() );

				message.editMessage( convertSlackMessage( slackMessage.toString(), null ) ).queue();
			}
		};

		slackSession.addMessageUpdatedListener( slackMessageUpdatedListener );
	}

	/**
	 * Adds the message delted listener to the Slack session. Currently just
	 * finds the message in the history and deletes it, along with deleting it
	 * from Discord.
	 */
	private void addMessageDeletedListener()
	{
		SlackMessageDeletedListener slackMessageDeletedListener = ( event, session ) ->
		{
			// Message deleted on slack
			Message discordMsg = messageHistory.removeDiscordMessage( event.getMessageTimestamp() );
			if ( discordMsg != null )
			{
				LOG.debug( "Slack message deleted" );
				discordMsg.delete().complete();
			}
		};

		slackSession.addMessageDeletedListener( slackMessageDeletedListener );
	}

	/**
	 * Adds the reaction added listener to the Slack session. Currently gets
	 * the reaction alias and converts it to a unicode value for Discord then
	 * adds it to the message.
	 */
	private void addReactionAddedListener()
	{
		ReactionAddedListener slackReactionAddedListener = ( event, session ) ->
		{
			final String timestamp = event.getMessageID();
			Message message = messageHistory.getDiscordMessage( timestamp );
			if ( message != null )
			{
				Emoji emoji = EmojiManager.getForAlias( event.getEmojiName() );
				message.addReaction( emoji.getUnicode() ).queue( o -> updateDiscordMessage( timestamp, message ) );
			}
		};

		slackSession.addReactionAddedListener( slackReactionAddedListener );
	}

	/**
	 * Adds the reaction removed listener to remove the corresponding reaction
	 * from the Discord message.
	 */
	private void addReactionRemovedListener()
	{
		ReactionRemovedListener slackReactionRemovedListener = ( event, session ) ->
		{
			final String timestamp = event.getMessageID();
			Message message = messageHistory.getDiscordMessage( timestamp );
			if ( message != null )
			{
				// Find the first instance of the removed emote on the message
				MessageReaction msgReaction = message.getReactions().stream()
						.filter( reaction ->
						{
							String reactionEmoji = reaction.getReactionEmote().getName();
							String slackEmoji = EmojiManager.getForAlias( event.getEmojiName() ).getUnicode();
							return reactionEmoji.equalsIgnoreCase( slackEmoji );
						} )
						.findFirst()
						.orElseThrow( () -> new IllegalArgumentException() );
				// Remove the reaction and update the message history
				msgReaction.removeReaction().queue( o -> updateDiscordMessage( timestamp, message ) );
			}
		};

		slackSession.addReactionRemovedListener( slackReactionRemovedListener );
	}

	/**
	 * Updates the message history object with a new message.
	 * @param timestamp Slack message timestamp.
	 * @param message Discord message.
	 */
	private void updateDiscordMessage( String timestamp, Message message )
	{
		final Long messageID = message.getIdLong();
		messageHistory.saveDiscordMessage( timestamp,
				message.getChannel().getMessageById( messageID ).complete() );
	}

	/**
	 * Converts a Slack message to Discord message style.
	 *
	 * @param slackMessage The slack message sent.
	 * @param sender The sender of the message OR null if the sender is unknown.
	 * @return
	 */
	private Message convertSlackMessage( String slackMessage, SlackUser sender )
	{
		MessageBuilder discordMessage = new MessageBuilder();

		// If we know who the sender is, add them to the start
		if ( sender != null )
		{
			discordMessage.append( "**" ).append( sender.getUserName() ).append( "**: " );
		}

		// Break the message up to parts that are either the message content
		// or the user mentions.
		String[] messageParts = slackMessage.split(
				String.format( Constants.WITH_DELIMITER, "<@U[A-z0-9]{8}>" ) );

		Arrays.stream( messageParts ).forEach( part ->
		{
			// Convert user mentions.
			if ( part.startsWith( "<@" ) )
			{
				String userId = part.substring( 2, part.length() - 1 );
				LOG.debug( "Finding user with id: " + userId );
				try
				{
					User user = jda.getUserById( UserBiMap.get( userId ) );
					discordMessage.append( user );
				} catch ( IllegalArgumentException e )
				{
					LOG.warn( "Could not find user" );
					discordMessage.append( "@" )
							.append( slackSession.getUsers().stream()
								.filter( user -> user.getId().equalsIgnoreCase( userId ) )
								.findFirst()
								.get().getUserName() );
				}
			}
			else
			{
				// Append message text.
				discordMessage.append( part );
			}
		} );

		return discordMessage.build();
	}
}
