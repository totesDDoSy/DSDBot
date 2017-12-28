package com.csanford.dsdbot;

import com.ullink.slack.simpleslackapi.SlackChannel;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.SlackUser;
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import javax.security.auth.login.LoginException;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.Message.MentionType;
import net.dv8tion.jda.core.entities.User;
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
		= ( SlackMessagePosted event, SlackSession session1 ) ->
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
		jda.getTextChannelsByName( Constants.DISCORD_CHANNEL, true ).get( 0 )
			.sendMessage( discordMessage.toString() ).complete();
	    }
	};
	session.addMessagePostedListener( slackMessagePostedListener );
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
	    slackSession.sendMessage( channel, slackMessage.toString() );
	}
    }
}
