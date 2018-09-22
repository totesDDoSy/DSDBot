package com.csanford.dsdbot;

import com.csanford.dsdbot.constants.SecureConstants;
import com.csanford.dsdbot.connector.DiscordConnector;
import com.csanford.dsdbot.connector.SlackConnector;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import java.io.IOException;
import javax.security.auth.login.LoginException;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.exceptions.RateLimitedException;

/**
 *
 * @author csanford
 * @date Dec 27, 2017
 */
public class App
{

	public static void main( String[] args ) throws LoginException, IllegalArgumentException, InterruptedException, RateLimitedException, IOException
	{
		// Create message history
		MessageHistory messageHistory = new MessageHistory();

		// Setup Discord API connection
		JDA jda = new JDABuilder( AccountType.BOT )
				.setToken( SecureConstants.DISCORD_TOKEN ).buildBlocking();

		// Setup Slack Connector
		SlackConnector slackConnector = new SlackConnector( jda, messageHistory );
		slackConnector.addListeners();

		// Setup Discord Connector
		DiscordConnector discordConnector
				= new DiscordConnector( slackConnector.getSlackSession(), messageHistory );
		jda.addEventListener( discordConnector );
		
		// Connect to slack server
		slackConnector.connect();
	}
}
