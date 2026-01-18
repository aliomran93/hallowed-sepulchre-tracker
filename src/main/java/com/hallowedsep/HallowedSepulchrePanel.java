package com.hallowedsep;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class HallowedSepulchrePanel extends PluginPanel
{
	// Color palette
	private static final Color BG_DARK = new Color(30, 30, 35);
	private static final Color BG_CARD = new Color(45, 47, 55);
	private static final Color PURPLE_ACCENT = new Color(170, 130, 255);
	private static final Color BLUE_ACCENT = new Color(100, 180, 255);
	private static final Color GOLD_ACCENT = new Color(255, 215, 0);
	private static final Color GREEN_SUCCESS = new Color(80, 200, 120);
	private static final Color ORANGE_WARN = new Color(255, 165, 80);
	private static final Color RED_DANGER = new Color(255, 100, 100);
	private static final Color TEXT_PRIMARY = new Color(255, 255, 255);
	private static final Color TEXT_SECONDARY = new Color(200, 200, 210);
	private static final Color TEXT_MUTED = new Color(140, 140, 150);
	
	// Floor colors
	private static final Color[] FLOOR_COLORS = {
		new Color(144, 238, 144), // Floor 1 - Light green
		new Color(135, 206, 235), // Floor 2 - Sky blue
		new Color(255, 255, 150), // Floor 3 - Yellow
		new Color(255, 165, 0),   // Floor 4 - Orange
		new Color(255, 100, 100)  // Floor 5 - Red
	};
	
	// Larger fonts for readability
	private static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 18);
	private static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 14);
	private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 13);
	private static final Font VALUE_FONT = new Font("SansSerif", Font.BOLD, 13);
	private static final Font SMALL_FONT = new Font("SansSerif", Font.PLAIN, 12);
	
	private final HallowedSepulchrePlugin plugin;
	private final HallowedSepulchreConfig config;
	
	private JPanel mainContent;
	private JScrollPane scrollPane;
	private boolean historyExpanded = false;
	
	public HallowedSepulchrePanel(HallowedSepulchrePlugin plugin, HallowedSepulchreConfig config)
	{
		super(false);
		this.plugin = plugin;
		this.config = config;
		
		setBackground(BG_DARK);
		setLayout(new BorderLayout());
		
		mainContent = new JPanel();
		mainContent.setBackground(BG_DARK);
		mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
		mainContent.setBorder(new EmptyBorder(10, 8, 20, 8));
		
		scrollPane = new JScrollPane(mainContent);
		scrollPane.setBackground(BG_DARK);
		scrollPane.setBorder(null);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		
		add(scrollPane, BorderLayout.CENTER);
		
		buildPanel();
	}
	
	private void buildPanel()
	{
		mainContent.removeAll();
		
		// === HEADER ===
		mainContent.add(createHeader());
		mainContent.add(Box.createVerticalStrut(12));
		
		PersistentStats stats = plugin.getPersistentStats();
		HallowedSepulchreSession session = plugin.getSession();
		
		// === TODAY'S STATS ===
		mainContent.add(createTodayCard(stats));
		mainContent.add(Box.createVerticalStrut(10));
		
		// === ALL-TIME STATS ===
		mainContent.add(createAllTimeCard(stats));
		mainContent.add(Box.createVerticalStrut(10));
		
		// === PROGRESS CARD ===
		mainContent.add(createProgressCard());
		mainContent.add(Box.createVerticalStrut(10));
		
		// === FLOOR COMPLETIONS ===
		mainContent.add(createFloorCard(stats, session));
		mainContent.add(Box.createVerticalStrut(10));
		
		// === BEST TIMES ===
		mainContent.add(createBestTimesCard(session));
		mainContent.add(Box.createVerticalStrut(10));
		
		// === DAILY HISTORY ===
		mainContent.add(createHistoryCard(stats));
		mainContent.add(Box.createVerticalStrut(15));
		
		// === RESET BUTTONS ===
		mainContent.add(createResetSessionButton());
		mainContent.add(Box.createVerticalStrut(8));
		mainContent.add(createResetAllTimeButton());
		
		mainContent.revalidate();
		mainContent.repaint();
	}
	
	private JPanel createHeader()
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(BG_DARK);
		header.setBorder(new EmptyBorder(5, 5, 5, 5));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
		
		JLabel title = new JLabel("Hallowed Sepulchre");
		title.setFont(TITLE_FONT);
		title.setForeground(PURPLE_ACCENT);
		title.setAlignmentX(Component.CENTER_ALIGNMENT);
		header.add(title);
		
		header.add(Box.createVerticalStrut(2));
		
		JLabel subtitle = new JLabel("Progress Tracker");
		subtitle.setFont(SMALL_FONT);
		subtitle.setForeground(TEXT_MUTED);
		subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
		header.add(subtitle);
		
		return header;
	}
	
	private JPanel createTodayCard(PersistentStats stats)
	{
		JPanel card = createCard("Today", BLUE_ACCENT);
		JPanel content = (JPanel) card.getComponent(1);
		
		DailyStats today = stats != null ? stats.getToday() : null;
		
		if (today == null || today.getRuns() == 0)
		{
			addStatRow(content, "No runs today", "--", TEXT_MUTED, TEXT_MUTED);
		}
		else
		{
			addStatRow(content, "Runs", String.valueOf(today.getRuns()), TEXT_SECONDARY, TEXT_PRIMARY);
			addStatRow(content, "XP", formatNumber(today.getTotalXp()), TEXT_SECONDARY, GREEN_SUCCESS);
			addStatRow(content, "Time", formatHoursMinutes(today.getHoursPlayed()), TEXT_SECONDARY, BLUE_ACCENT);
			
			// Live XP/hr including current run
			double xpHr = plugin.getXpPerHour();
			addStatRow(content, "XP/hr", formatNumber((int) xpHr), TEXT_SECONDARY, getXpHrColor(xpHr));
			
			if (today.getRuns() > 0)
			{
				int avgXp = today.getTotalXp() / today.getRuns();
				addStatRow(content, "Avg/Run", formatNumber(avgXp), TEXT_SECONDARY, TEXT_PRIMARY);
			}
		}
		
		return card;
	}
	
	private JPanel createAllTimeCard(PersistentStats stats)
	{
		JPanel card = createCard("All Time", GOLD_ACCENT);
		JPanel content = (JPanel) card.getComponent(1);
		
		if (stats == null || stats.getAllTimeRuns() == 0)
		{
			addStatRow(content, "No data yet", "--", TEXT_MUTED, TEXT_MUTED);
		}
		else
		{
			addStatRow(content, "Runs", String.valueOf(stats.getAllTimeRuns()), TEXT_SECONDARY, TEXT_PRIMARY);
			addStatRow(content, "XP", formatNumber(stats.getAllTimeXp()), TEXT_SECONDARY, GREEN_SUCCESS);
			addStatRow(content, "Time", formatHoursMinutes(stats.getAllTimeHours()), TEXT_SECONDARY, BLUE_ACCENT);
			addStatRow(content, "Days", String.valueOf(stats.getDaysTracked()), TEXT_SECONDARY, PURPLE_ACCENT);
			addStatRow(content, "Runs/Day", String.format("%.1f", stats.getAverageRunsPerDay()), TEXT_SECONDARY, TEXT_PRIMARY);
			addStatRow(content, "XP/hr", formatNumber((int) stats.getAllTimeXpPerHour()), TEXT_SECONDARY, getXpHrColor(stats.getAllTimeXpPerHour()));
		}
		
		return card;
	}
	
	private JPanel createProgressCard()
	{
		JPanel card = createCard("Progress to 99", GREEN_SUCCESS);
		JPanel content = (JPanel) card.getComponent(1);
		
		int level = plugin.getCurrentAgilityLevel();
		int xpTo99 = plugin.getXpToLevel(99);
		int runsTo99 = plugin.getRunsRemaining(99);
		
		// Level with colored indicator
		Color levelColor = level >= 99 ? GOLD_ACCENT : (level >= 92 ? GREEN_SUCCESS : TEXT_PRIMARY);
		addStatRow(content, "Level", String.valueOf(level), TEXT_SECONDARY, levelColor);
		
		// Progress
		if (level < 99)
		{
			PersistentStats stats = plugin.getPersistentStats();
			double avgRunsPerDay = stats != null ? stats.getAverageRunsPerDay() : 0;
			
			// XP to 92 if not there yet
			if (level < 92)
			{
				int xpTo92 = plugin.getXpToLevel(92);
				int runsTo92 = plugin.getRunsRemaining(92);
				addStatRow(content, "XP to 92", formatNumber(xpTo92), TEXT_SECONDARY, ORANGE_WARN);
				addStatRow(content, "Runs to 92", String.valueOf(runsTo92), TEXT_SECONDARY, BLUE_ACCENT);
				
				if (avgRunsPerDay > 0)
				{
					int daysTo92 = (int) Math.ceil(runsTo92 / avgRunsPerDay);
					addStatRow(content, "Days to 92", String.valueOf(daysTo92), TEXT_SECONDARY, PURPLE_ACCENT);
				}
			}
			
			// Always show 99 stats
			addStatRow(content, "XP to 99", formatNumber(xpTo99), TEXT_SECONDARY, ORANGE_WARN);
			addStatRow(content, "Runs to 99", String.valueOf(runsTo99), TEXT_SECONDARY, GOLD_ACCENT);
			
			if (avgRunsPerDay > 0)
			{
				int daysTo99 = (int) Math.ceil(runsTo99 / avgRunsPerDay);
				addStatRow(content, "Days to 99", String.valueOf(daysTo99), TEXT_SECONDARY, GOLD_ACCENT);
			}
		}
		else
		{
			content.add(Box.createVerticalStrut(10));
			JLabel congrats = new JLabel("99 AGILITY!");
			congrats.setFont(HEADER_FONT);
			congrats.setForeground(GOLD_ACCENT);
			congrats.setAlignmentX(Component.CENTER_ALIGNMENT);
			content.add(congrats);
			content.add(Box.createVerticalStrut(10));
		}
		
		return card;
	}
	
	private JPanel createFloorCard(PersistentStats stats, HallowedSepulchreSession session)
	{
		JPanel card = createCard("Floor Completions", PURPLE_ACCENT);
		JPanel content = (JPanel) card.getComponent(1);
		
		// Create a grid for floors
		JPanel floorGrid = new JPanel(new GridLayout(1, 5, 8, 0));
		floorGrid.setBackground(BG_CARD);
		floorGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
		
		for (int i = 1; i <= 5; i++)
		{
			int allTime = stats != null ? stats.getFloorCompletions(i) : 0;
			
			JPanel floorPanel = new JPanel();
			floorPanel.setLayout(new BoxLayout(floorPanel, BoxLayout.Y_AXIS));
			floorPanel.setBackground(new Color(35, 37, 45));
			floorPanel.setBorder(BorderFactory.createCompoundBorder(
				new LineBorder(FLOOR_COLORS[i-1].darker(), 2),
				new EmptyBorder(8, 4, 8, 4)
			));
			
			JLabel floorLabel = new JLabel("F" + i);
			floorLabel.setFont(SMALL_FONT);
			floorLabel.setForeground(FLOOR_COLORS[i-1]);
			floorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			
			JLabel countLabel = new JLabel(String.valueOf(allTime));
			countLabel.setFont(VALUE_FONT);
			countLabel.setForeground(allTime > 0 ? TEXT_PRIMARY : TEXT_MUTED);
			countLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			
			floorPanel.add(floorLabel);
			floorPanel.add(Box.createVerticalStrut(4));
			floorPanel.add(countLabel);
			
			floorGrid.add(floorPanel);
		}
		
		content.add(floorGrid);
		
		return card;
	}
	
	private JPanel createBestTimesCard(HallowedSepulchreSession session)
	{
		JPanel card = createCard("Personal Best Times", ORANGE_WARN);
		JPanel content = (JPanel) card.getComponent(1);
		
		PersistentStats stats = plugin.getPersistentStats();
		boolean hasTimes = false;
		
		// Show personal best times from game data
		for (int i = 1; i <= 5; i++)
		{
			long pbMs = stats != null ? stats.getBestFloorTimeMs(i) : 0;
			String timeStr = pbMs > 0 ? formatDurationMs(pbMs) : "--";
			if (pbMs > 0) hasTimes = true;
			addStatRow(content, "Floor " + i, timeStr, TEXT_SECONDARY, FLOOR_COLORS[i-1]);
		}
		
		if (!hasTimes)
		{
			// Show message if no PBs yet
			addStatRow(content, "Complete floors to see PBs", "", TEXT_MUTED, TEXT_MUTED);
		}
		
		return card;
	}
	
	private String formatDurationMs(long ms)
	{
		long totalSeconds = ms / 1000;
		long minutes = totalSeconds / 60;
		long seconds = totalSeconds % 60;
		return String.format("%d:%02d", minutes, seconds);
	}
	
	private JPanel createHistoryCard(PersistentStats stats)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(BG_CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			new LineBorder(BLUE_ACCENT.darker(), 1),
			new EmptyBorder(0, 0, 0, 0)
		));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 800));
		card.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		// Clickable header
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(new Color(25, 45, 64));
		header.setBorder(new EmptyBorder(10, 14, 10, 14));
		header.setCursor(new Cursor(Cursor.HAND_CURSOR));
		
		int totalDays = stats != null ? stats.getDailyHistory().size() : 0;
		String arrow = historyExpanded ? "v" : ">";
		JLabel titleLabel = new JLabel(arrow + " Daily History (" + totalDays + " days)");
		titleLabel.setFont(HEADER_FONT);
		titleLabel.setForeground(BLUE_ACCENT);
		header.add(titleLabel, BorderLayout.WEST);
		
		// Click to toggle
		header.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				historyExpanded = !historyExpanded;
				updateStats();
			}
			@Override
			public void mouseEntered(java.awt.event.MouseEvent e) {
				header.setBackground(new Color(35, 55, 75));
			}
			@Override
			public void mouseExited(java.awt.event.MouseEvent e) {
				header.setBackground(new Color(25, 45, 64));
			}
		});
		
		card.add(header);
		
		// Content area (only visible when expanded)
		if (historyExpanded)
		{
			JPanel content = new JPanel();
			content.setLayout(new BorderLayout());
			content.setBackground(BG_CARD);
			content.setBorder(new EmptyBorder(12, 14, 12, 14));
			
			// Inner panel for rows, left-aligned
			JPanel rowsPanel = new JPanel();
			rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
			rowsPanel.setBackground(BG_CARD);
			rowsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			
			if (stats == null || stats.getDailyHistory().isEmpty())
			{
				addStatRow(rowsPanel, "No history yet", "--", TEXT_MUTED, TEXT_MUTED);
			}
			else
			{
				// Show ALL days when expanded
				Map<String, DailyStats> history = stats.getDailyHistory();
				DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd");
				
				int count = 0;
				for (int daysAgo = 0; daysAgo < 365 && count < 100; daysAgo++)
				{
					String dateKey = LocalDate.now().minusDays(daysAgo).toString();
					DailyStats day = history.get(dateKey);
					
					if (day != null && day.getRuns() > 0)
					{
						String dateLabel = daysAgo == 0 ? "Today" : 
							daysAgo == 1 ? "Yesterday" : 
							LocalDate.parse(dateKey).format(fmt);
						
						String runsText = day.getRuns() == 1 ? "1 run" : day.getRuns() + " runs";
						String stats_str = runsText + " / " + formatNumber(day.getTotalXp()) + " / " + formatHoursMinutes(day.getHoursPlayed());
						addHistoryRow(rowsPanel, dateLabel, stats_str);
						count++;
					}
				}
				
				if (count == 0)
				{
					addStatRow(rowsPanel, "No recent activity", "--", TEXT_MUTED, TEXT_MUTED);
				}
			}
			
			content.add(rowsPanel, BorderLayout.WEST);
			card.add(content);
		}
		
		return card;
	}
	
	private void addHistoryRow(JPanel panel, String date, String stats)
	{
		// Use BorderLayout for proper left alignment
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(BG_CARD);
		row.setBorder(new EmptyBorder(4, 0, 4, 0));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		// Left side: date and stats stacked
		JPanel leftContent = new JPanel();
		leftContent.setLayout(new BoxLayout(leftContent, BoxLayout.Y_AXIS));
		leftContent.setBackground(BG_CARD);
		leftContent.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		JLabel dateLabel = new JLabel(date);
		dateLabel.setFont(VALUE_FONT);
		dateLabel.setForeground(PURPLE_ACCENT);
		
		JLabel statsLabel = new JLabel(stats);
		statsLabel.setFont(SMALL_FONT);
		statsLabel.setForeground(TEXT_SECONDARY);
		
		leftContent.add(dateLabel);
		leftContent.add(statsLabel);
		
		row.add(leftContent, BorderLayout.WEST);
		
		panel.add(row);
	}
	
	private JPanel createCard(String title, Color accentColor)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(BG_CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			new LineBorder(accentColor.darker(), 1),
			new EmptyBorder(0, 0, 0, 0)
		));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 500));
		card.setAlignmentX(Component.CENTER_ALIGNMENT);
		
		// Header bar
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(new Color(
			Math.max(0, accentColor.getRed() / 4),
			Math.max(0, accentColor.getGreen() / 4),
			Math.max(0, accentColor.getBlue() / 4)
		));
		header.setBorder(new EmptyBorder(10, 14, 10, 14));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		
		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(HEADER_FONT);
		titleLabel.setForeground(accentColor);
		header.add(titleLabel, BorderLayout.WEST);
		
		// Content area
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(BG_CARD);
		content.setBorder(new EmptyBorder(12, 14, 12, 14));
		
		card.add(header);
		card.add(content);
		
		return card;
	}
	
	private void addStatRow(JPanel panel, String label, String value, Color labelColor, Color valueColor)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(BG_CARD);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		
		JLabel labelComp = new JLabel(label);
		labelComp.setFont(LABEL_FONT);
		labelComp.setForeground(labelColor);
		
		JLabel valueComp = new JLabel(value);
		valueComp.setFont(VALUE_FONT);
		valueComp.setForeground(valueColor);
		
		row.add(labelComp, BorderLayout.WEST);
		row.add(valueComp, BorderLayout.EAST);
		
		panel.add(row);
	}
	
	private JButton createResetSessionButton()
	{
		JButton btn = new JButton("Reset Session");
		btn.setFont(LABEL_FONT);
		btn.setBackground(new Color(50, 50, 60));
		btn.setForeground(new Color(200, 200, 210));
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
		btn.setAlignmentX(Component.CENTER_ALIGNMENT);
		btn.setMaximumSize(new Dimension(200, 32));
		btn.setPreferredSize(new Dimension(200, 32));
		
		btn.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseEntered(java.awt.event.MouseEvent e) {
				btn.setBackground(new Color(70, 70, 80));
			}
			public void mouseExited(java.awt.event.MouseEvent e) {
				btn.setBackground(new Color(50, 50, 60));
			}
		});
		
		btn.addActionListener(e -> {
			int result = JOptionPane.showConfirmDialog(
				this,
				"Reset current session data?\n(All-time stats will be kept)",
				"Reset Session",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE
			);
			
			if (result == JOptionPane.YES_OPTION)
			{
				plugin.resetSession();
				updateStats();
			}
		});
		
		return btn;
	}
	
	private JButton createResetAllTimeButton()
	{
		JButton btn = new JButton("Reset ALL Stats");
		btn.setFont(LABEL_FONT);
		btn.setBackground(new Color(70, 35, 35));
		btn.setForeground(RED_DANGER);
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
		btn.setAlignmentX(Component.CENTER_ALIGNMENT);
		btn.setMaximumSize(new Dimension(200, 32));
		btn.setPreferredSize(new Dimension(200, 32));
		
		btn.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseEntered(java.awt.event.MouseEvent e) {
				btn.setBackground(new Color(100, 40, 40));
			}
			public void mouseExited(java.awt.event.MouseEvent e) {
				btn.setBackground(new Color(70, 35, 35));
			}
		});
		
		btn.addActionListener(e -> {
			int result = JOptionPane.showConfirmDialog(
				this,
				"WARNING!\n\nThis will permanently delete:\n" +
				"- All-time runs, XP, and time\n" +
				"- All daily history\n" +
				"- All floor completions\n" +
				"- All best times\n\n" +
				"This cannot be undone!\n\n" +
				"Are you sure?",
				"Reset ALL Stats",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE
			);
			
			if (result == JOptionPane.YES_OPTION)
			{
				// Double confirm for safety
				int confirm = JOptionPane.showConfirmDialog(
					this,
					"Are you REALLY sure?\nAll your progress will be lost forever.",
					"Final Confirmation",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.ERROR_MESSAGE
				);
				
				if (confirm == JOptionPane.YES_OPTION)
				{
					plugin.resetAllStats();
					updateStats();
				}
			}
		});
		
		return btn;
	}
	
	public void updateStats()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::updateStats);
			return;
		}
		
		buildPanel();
	}
	
	// Utility methods
	private String formatNumber(int num)
	{
		if (num >= 1000000)
		{
			return String.format("%.2fM", num / 1000000.0);
		}
		else if (num >= 1000)
		{
			return String.format("%.1fK", num / 1000.0);
		}
		return String.valueOf(num);
	}
	
	private String formatDuration(Duration duration)
	{
		if (duration == null) return "--";
		long totalSeconds = duration.getSeconds();
		long minutes = totalSeconds / 60;
		long seconds = totalSeconds % 60;
		return String.format("%d:%02d", minutes, seconds);
	}
	
	private String formatHoursMinutes(double hours)
	{
		int totalMinutes = (int) (hours * 60);
		int h = totalMinutes / 60;
		int m = totalMinutes % 60;
		
		if (h > 0)
		{
			return String.format("%dh %dm", h, m);
		}
		return String.format("%dm", m);
	}
	
	private Color getXpHrColor(double xpPerHour)
	{
		if (xpPerHour >= 100000) return new Color(0, 255, 0);
		if (xpPerHour >= 80000) return GREEN_SUCCESS;
		if (xpPerHour >= 60000) return GOLD_ACCENT;
		return ORANGE_WARN;
	}
}
