package com.csanford.dsdbot.connector;

import com.csanford.dsdbot.constants.Constants;
import com.csanford.dsdbot.MessageHistory;
import com.csanford.dsdbot.constants.UserBiMap;
import com.ullink.slack.simpleslackapi.SlackChannel;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.vdurmont.emoji.Emoji;
import com.vdurmont.emoji.EmojiManager;
import java.util.Arrays;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageDeleteEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.MessageUpdateEvent;
import net.dv8tion.jda.core.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.core.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a class that extends ListenerAdapter and can be attached to a JDA
 * connection.
 *
 * @author csanford
 * #date Sep 22, 2018
 */
public class DiscordConnector extends ListenerAdapter
{

	private static final Logger LOG = LoggerFactory.getLogger( DiscordConnector.class );

	private final SlackSession slackSession;
	private final MessageHistory messageHistory;

	/**
	 * Create the Discord to Slack connector.
	 *
	 * @param slackSession The Slack session we're talking to.
	 * @param messageHistory A message history to keep track of sent messages.
	 */
	public DiscordConnector( SlackSession slackSession, MessageHistory messageHistory )
	{
		this.slackSession = slackSession;
		this.messageHistory = messageHistory;
	}

	/**
	 * Message received from Discord listener. Currently takes the message and
	 * posts it to Slack, making sure it wasn't the bot that posted the message.
	 *
	 * @param event The event.
	 */
	@Override
	public void onMessageReceived( MessageReceivedEvent event )
	{
		// Discord Message Listener
		Message discordMessage = event.getMessage();
		User messageAuthor = event.getAuthor();
		Member selfMember = event.getGuild().getSelfMember();
		if ( discordMessage.isMentioned( selfMember, Message.MentionType.USER ) && !messageAuthor.isBot() )
		{
			LOG.debug( "Discord message recieved from " + messageAuthor.getName() );
			// If the bot was mentioned by not itself, send a slack message
			String slackMessage = convertDiscordMessage( messageAuthor,
					discordMessage );

			SlackChannel channel = slackSession.findChannelByName( Constants.SLACK_CHANNEL );
			String timestamp
					= slackSession.sendMessage( channel, slackMessage ).getReply().getTimestamp();
			messageHistory.saveSlackMessage( event.getMessageIdLong(), timestamp );
		}
	}

	/**
	 * Message deleted from Discord listener. Currently just finds the slack
	 * message and deletes it.
	 *
	 * @param event The event.
	 */
	@Override
	public void onMessageDelete( MessageDeleteEvent event )
	{
		// Message deleted from Discord
		String timestamp = messageHistory.removeSlackMessage( event.getMessageIdLong() );
		if ( timestamp != null )
		{
			LOG.debug( "Discord message deleted" );
			SlackChannel channel = slackSession.findChannelByName( Constants.SLACK_CHANNEL );
			slackSession.deleteMessage( timestamp, channel );
		}
	}

	/**
	 * Message updated in Discord listener. Currently gets the message from the
	 * history and updates the corresponding message in Slack.
	 *
	 * @param event The event.
	 */
	@Override
	public void onMessageUpdate( MessageUpdateEvent event )
	{
		String timestamp = messageHistory.getSlackTimestamp( event.getMessageIdLong() );
		if ( timestamp != null )
		{
			User author = event.getAuthor();
			Message discordMessage = event.getMessage();
			String slackMessage = convertDiscordMessage( author, discordMessage );
			SlackChannel channel = slackSession.findChannelByName( Constants.SLACK_CHANNEL );
			slackSession.updateMessage( timestamp, channel, slackMessage );
		}
	}

	/**
	 * Reaction added to Discord message listener. Currently gets the message
	 * from the history and adds the corresponding emoji alias.
	 *
	 * @param event The event.
	 */
	@Override
	public void onMessageReactionAdd( MessageReactionAddEvent event )
	{
		String timestamp = messageHistory.getSlackTimestamp( event.getMessageIdLong() );
		if ( timestamp != null )
		{
			SlackChannel channel = slackSession.findChannelByName( Constants.SLACK_CHANNEL );
			String emote = event.getReaction().getReactionEmote().getName();
			Emoji emoji = EmojiManager.getByUnicode( emote );
			slackSession.addReactionToMessage( channel, timestamp, emoji.getAliases().get( 0 ) );
		}
	}

	/**
	 * Reaction removed from Discord message listener. Currently gets the
	 * message from the history and removes the corresponding emoji alias.
	 *
	 * @param event The event.
	 */
	@Override
	public void onMessageReactionRemove( MessageReactionRemoveEvent event )
	{
		String timestamp = messageHistory.getSlackTimestamp( event.getMessageIdLong() );
		if ( timestamp != null )
		{
			SlackChannel channel = slackSession.findChannelByName( Constants.SLACK_CHANNEL );
			String emote = event.getReaction().getReactionEmote().getName();
			Emoji emoji = EmojiManager.getByUnicode( emote );
			slackSession.removeReactionFromMessage( channel, timestamp, emoji.getAliases().get( 0 ) );
		}
	}

	/**
	 * Prepends the name of the sender to the message.
	 *
	 * @param author Original author of the message.
	 * @param message The message that was sent.
	 * @return A strick formatted for Slack display in the style of
	 * <b>{author}</b>: {content}
	 */
	private String convertDiscordMessage( User author, Message message )
	{
		StringBuilder slackMessage = new StringBuilder();
		slackMessage.append( "*" ).append( author.getName() ).append( "*: " );

		String[] messageParts = message.getContentRaw()
				.split( String.format( Constants.WITH_DELIMITER, "<@!?(\\d){17,18}>" ) );
		Arrays.stream( messageParts ).forEach( part ->
		{
			if ( part.startsWith( "<@" ) )
			{
				String discordId = part.replace( "!", "" ).replace( "<@", "" ).replace( ">", "" );
				slackMessage.append( "<@" )
						.append( UserBiMap.getOrDefault( discordId, part ) )
						.append( ">" );
			} else
			{
				slackMessage.append( part );
			}
		} );

		return slackMessage.toString();
	}
}
