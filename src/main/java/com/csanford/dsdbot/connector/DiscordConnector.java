package com.csanford.dsdbot.connector;

import com.csanford.dsdbot.constants.Constants;
import com.csanford.dsdbot.MessageHistory;
import com.ullink.slack.simpleslackapi.SlackChannel;
import com.ullink.slack.simpleslackapi.SlackMessageHandle;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.replies.SlackMessageReply;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageDeleteEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.MessageUpdateEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

/**
 * Provides a class that extends ListenerAdapter and can be attached to a JDA connection.
 * @author csanford
 * @date Sep 22, 2018
 */
public class DiscordConnector extends ListenerAdapter
{

	private final SlackSession slackSession;
	private final MessageHistory messageHistory;

	/**
	 * Create the Discord to Slack connector.
	 * @param slackSession The Slack session we're talking to.
	 * @param messageHistory A message history to keep track of sent messages.
	 */
	public DiscordConnector( SlackSession slackSession, MessageHistory messageHistory )
	{
		this.slackSession = slackSession;
		this.messageHistory = messageHistory;
	}

	/**
	 * Message received from Discord listener.
	 * Currently takes the message and posts it to Slack,
	 * making sure it wasn't the bot that posted the message.
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
			// If the bot was mentioned by not itself, send a slack message
			String slackMessage = convertDiscordMessage( messageAuthor.getName(),
					discordMessage.getContentDisplay() );

			SlackChannel channel = slackSession.findChannelByName( Constants.SLACK_CHANNEL );
			String timestamp
					= slackSession.sendMessage( channel, slackMessage ).getReply().getTimestamp();
			messageHistory.saveSlackMessage( event.getMessageIdLong(), timestamp );
		}
	}

	/**
	 * Message deleted from Discord listener.
	 * Currently just finds the slack message and deletes it.
	 * @param event 
	 */
	@Override
	public void onMessageDelete( MessageDeleteEvent event )
	{
		// Message deleted from Discord
		String timestamp = messageHistory.removeSlackMessage( event.getMessageIdLong() );
		if ( timestamp != null )
		{
			SlackChannel channel = slackSession.findChannelByName( Constants.SLACK_CHANNEL );
			slackSession.deleteMessage( timestamp, channel );
		}
	}

	/**
	 * Message updated in Discord listener.
	 * Currently gets the message from the history and updates the corresponding message
	 * in Slack.
	 * @param event 
	 */
	@Override
	public void onMessageUpdate( MessageUpdateEvent event )
	{
		String timestamp = messageHistory.getSlackTimestamp( event.getMessageIdLong() );
		if ( timestamp != null )
		{
			String author = event.getAuthor().getName();
			String discordMessage = event.getMessage().getContentDisplay();
			String slackMessage = convertDiscordMessage( author, discordMessage );
			SlackChannel channel = slackSession.findChannelByName( Constants.SLACK_CHANNEL );
			slackSession.updateMessage( timestamp, channel, slackMessage );
		}
	}

	/**
	 * Prepends the name of the sender to the message.
	 * @param name Name to prepend, usually the person sending the message.
	 * @param contentDisplay The actual message content.
	 * @return A strick formatted for Slack display in the style of <b>{name}</b>: {contentDisplay}
	 */
	private String convertDiscordMessage( String name, String contentDisplay )
	{
		StringBuilder slackMessage = new StringBuilder();

		slackMessage.append( "*" ).append( name ).append( "*: " );
		slackMessage.append( contentDisplay );
		return slackMessage.toString();
	}
}
