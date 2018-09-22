package com.csanford.dsdbot;

import com.ullink.slack.simpleslackapi.SlackChannel;
import com.ullink.slack.simpleslackapi.SlackMessageHandle;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.SlackUser;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import com.ullink.slack.simpleslackapi.listeners.SlackMessageDeletedListener;
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener;
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
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

/**
 *
 * @author csanford
 * @date Dec 27, 2017
 */
public class App extends ListenerAdapter
{

    private final SlackSession slackSession;
    private static Map< String, Message > stodMessages = new HashMap<>();
    private static Queue<String> stodHistory = new ConcurrentLinkedQueue<>();
    private static Map< Long, String > dtosMessages = new HashMap<>();
    private static Queue< Long > dtosHistory = new ConcurrentLinkedQueue<>();

    public App( SlackSession slackSession )
    {
	this.slackSession = slackSession;
    }

    public static void main( String[] args ) throws LoginException, IllegalArgumentException, InterruptedException, RateLimitedException, IOException
    {
	// Setup Slack API Connection
	SlackSession session = SlackSessionFactory.getSlackSessionBuilder( SecureConstants.SLACK_TOKEN ).build();
	session.connect();

	// Setup Discord API connection
	JDA jda = new JDABuilder( AccountType.BOT )
		.setToken( SecureConstants.DISCORD_TOKEN ).buildBlocking();
	jda.addEventListener( new App( session ) );

	SlackMessagePostedListener slackMessagePostedListener
		= ( event, session1 ) ->
	{
	    // Slack Message Listener
	    String slackMessage = event.getMessageContent();
	    String selfId = session1.sessionPersona().getId();
	    SlackUser sender = event.getSender();
	    if ( !session1.sessionPersona().getId().equals( sender.getId() )
		    && slackMessage.contains( "@" + selfId ) )
	    {
		// If the bot didn't send the message and was mentioned, send a discord message
		StringBuilder discordMessage = new StringBuilder();

		// Make a map for user IDs to names
		Map<String, String> usersMap = session1.getUsers().stream()
			.collect( Collectors.toMap( user -> user.getId(), user -> user.getUserName() ) );
		for ( Map.Entry<String, String> entry : usersMap.entrySet() )
		{
		    slackMessage = slackMessage
			    .replaceAll( entry.getKey(), entry.getValue() )
			    .replaceAll( "[<>]", "" );
		}

		discordMessage.append( "**" ).append( sender.getUserName() ).append( "**: " );
		discordMessage.append( slackMessage );

		// Send the discord message
		Message message = jda.getTextChannelsByName( Constants.DISCORD_CHANNEL, true ).get( 0 )
			.sendMessage( discordMessage.toString() ).complete();
		
		saveDiscordMessage( event.getTimestamp(), message );
	    }
	};
	
	SlackMessageDeletedListener slackMessageDeletedListener = ( event, session2 ) ->
	{
	    // Message deleted on slack
	    String selfId = session2.sessionPersona().getId();
	    Message discordMsg = stodMessages.remove( event.getMessageTimestamp() );
	    if ( discordMsg != null )
	    {
		discordMsg.delete().complete();
	    }
	};
	
	session.addMessagePostedListener( slackMessagePostedListener );
	session.addMessageDeletedListener( slackMessageDeletedListener );
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
	    StringBuilder slackMessage = new StringBuilder();

	    slackMessage.append( "*" ).append( messageAuthor.getName() ).append( "*: ");
	    slackMessage.append( discordMessage.getContentDisplay() );
	    
	    SlackChannel channel = slackSession.findChannelByName( Constants.SLACK_CHANNEL );
	    SlackMessageHandle<SlackMessageReply> sendMessage = 
		    slackSession.sendMessage( channel, slackMessage.toString() );
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
    
    /**
     * Save a discord message with a slack timestamp.
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
}
