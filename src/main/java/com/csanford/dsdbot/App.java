package com.csanford.dsdbot;

import com.ullink.slack.simpleslackapi.SlackChannel;
import com.ullink.slack.simpleslackapi.SlackMessageHandle;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.SlackUser;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import com.ullink.slack.simpleslackapi.listeners.SlackMessageDeletedListener;
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener;
import com.ullink.slack.simpleslackapi.listeners.SlackMessageUpdatedListener;
import com.ullink.slack.simpleslackapi.replies.SlackMessageReply;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import javax.security.auth.login.LoginException;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.Message.MentionType;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageDeleteEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.message.MessageUpdateEvent;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

/**
 *
 * @author csanford
 * @date Dec 27, 2017
 */
public class App extends ListenerAdapter
{

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

    private final SlackSession slackSession;
    private static Map< String, Message> stodMessages = new HashMap<>();
    private static Queue< String> stodHistory = new ConcurrentLinkedQueue<>();
    private static Map< Long, String> dtosMessages = new HashMap<>();
    private static Queue< Long> dtosHistory = new ConcurrentLinkedQueue<>();

    public App( SlackSession slackSession )
    {
	this.slackSession = slackSession;
    }

    public static void main( String[] args ) throws LoginException, IllegalArgumentException, InterruptedException, RateLimitedException, IOException
    {
	// Setup Slack API Connection
	SlackSession slackSession = SlackSessionFactory.getSlackSessionBuilder( SecureConstants.SLACK_TOKEN ).build();
	slackSession.connect();

	// Setup Discord API connection
	JDA jda = new JDABuilder( AccountType.BOT )
		.setToken( SecureConstants.DISCORD_TOKEN ).buildBlocking();
	jda.addEventListener( new App( slackSession ) );

	SlackMessagePostedListener slackMessagePostedListener
		= ( event, session ) ->
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

		saveDiscordMessage( event.getTimestamp(), message );
	    }
	};

	SlackMessageDeletedListener slackMessageDeletedListener = ( event, session ) ->
	{
	    // Message deleted on slack
	    Message discordMsg = stodMessages.remove( event.getMessageTimestamp() );
	    if ( discordMsg != null )
	    {
		discordMsg.delete().complete();
	    }
	};

	SlackMessageUpdatedListener slackMessageUpdatedListener = ( event, session ) ->
	{
	    Message message = stodMessages.get( event.getMessageTimestamp() );
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

	slackSession.addMessagePostedListener( slackMessagePostedListener );
	slackSession.addMessageDeletedListener( slackMessageDeletedListener );
	slackSession.addMessageUpdatedListener( slackMessageUpdatedListener );
    }

    @Override
    public void onMessageReceived( MessageReceivedEvent event )
    {
	// Discord Message Listener
	Message discordMessage = event.getMessage();
	User messageAuthor = event.getAuthor();
	Member selfMember = event.getGuild().getSelfMember();
	if ( discordMessage.isMentioned( selfMember, MentionType.USER ) && !messageAuthor.isBot() )
	{
	    // If the bot was mentioned by not itself, send a slack message
	    String slackMessage = convertDiscordMessage( messageAuthor.getName(),
		    discordMessage.getContentDisplay() );

	    SlackChannel channel = slackSession.findChannelByName( Constants.SLACK_CHANNEL );
	    SlackMessageHandle<SlackMessageReply> sendMessage
		    = slackSession.sendMessage( channel, slackMessage );
	    saveSlackMessage( event.getMessageIdLong(), sendMessage.getReply().getTimestamp() );
	}
    }

    @Override
    public void onMessageDelete( MessageDeleteEvent event )
    {
	// Message deleted from Discord
	String timestamp = dtosMessages.remove( event.getMessageIdLong() );
	if ( timestamp != null )
	{
	    SlackChannel channel = slackSession.findChannelByName( Constants.SLACK_CHANNEL );
	    slackSession.deleteMessage( timestamp, channel );
	}
    }

    @Override
    public void onMessageUpdate( MessageUpdateEvent event )
    {
	String timestamp = dtosMessages.get( event.getMessageIdLong() );
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
     * Save a discord message with a slack timestamp.
     *
     * @param timestamp Timestamp of slack message.
     * @param message Discord message object.
     */
    private static void saveDiscordMessage( String timestamp, Message message )
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
     * @param messageId The Discord long message id.
     * @param timestamp Timestamp of slack message.
     */
    public static void saveSlackMessage( Long messageId, String timestamp )
    {
	dtosHistory.add( messageId );
	if ( dtosHistory.size() > Constants.MAX_MSG_HISTORY - 1 )
	{
	    // Log this probably
	    dtosMessages.remove( dtosHistory.remove() );
	}
	dtosMessages.put( messageId, timestamp );
    }

    private String convertDiscordMessage( String name, String contentDisplay )
    {
	StringBuilder slackMessage = new StringBuilder();

	slackMessage.append( "*" ).append( name ).append( "*: " );
	slackMessage.append( contentDisplay );
	return slackMessage.toString();
    }
}
