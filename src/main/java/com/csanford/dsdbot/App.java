package com.csanford.dsdbot;

import com.csanford.dsdbot.constants.SecureConstants;
import com.csanford.dsdbot.connector.DiscordConnector;
import com.csanford.dsdbot.connector.SlackConnector;
import java.io.IOException;
import javax.security.auth.login.LoginException;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.exceptions.RateLimitedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author csanford
 * #date Dec 27, 2017
 */
public class App
{

    private static final Logger LOG = LoggerFactory.getLogger( App.class );

    public static void main( String[] args ) throws LoginException, IllegalArgumentException, InterruptedException, RateLimitedException, IOException
    {
	// Create message history
	MessageHistory messageHistory = new MessageHistory();

	// Setup Discord API connection
	LOG.info( "Connecting to Discord" );
	JDA jda = new JDABuilder( AccountType.BOT )
		.setToken( SecureConstants.DISCORD_TOKEN ).buildBlocking();

	// Setup Slack Connector
	LOG.info( "Creating Slack Connector" );
	SlackConnector slackConnector = new SlackConnector( jda, messageHistory );
	slackConnector.addListeners();

	// Setup Discord Connector
	LOG.info( "Creating Discord Connector" );
	DiscordConnector discordConnector
		= new DiscordConnector( slackConnector.getSlackSession(), messageHistory );
	LOG.info( "Binding JDA Event Listener" );
	jda.addEventListener( discordConnector );

	// Connect to slack server
	LOG.info( "Connecting to Slack" );
	slackConnector.connect();
    }
}
