package dbsautoshop;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class DBSAutoShop extends JavaPlugin {
	
	private Connection con;
	
	@Override
	public void onEnable() {
		saveDefaultConfig();
		reloadConfig();
		
		try {
			con = DriverManager.getConnection("jdbc:mysql://" + getConfig().getString("MySQL.host") + "/"
					+ getConfig().getString("MySQL.db"), getConfig().getString("MySQL.user"), 
					getConfig().getString("MySQL.pass"));
		} catch (SQLException e) {
			getLogger().severe("*** Nao foi possivel conectar ao banco de dados:\n" + e.getMessage());
			setEnabled(false);
			return;
		}
		
		new BukkitRunnable() {
			@Override
			public void run() {
				try {
					check();
					checkChargeback();
				} catch (SQLException e) {
					getLogger().severe("*** Erro ao realizar checagem:\n" + e.getMessage());
				}
			}
		}.runTaskTimerAsynchronously(this, 0L, getConfig().getInt("tempo-checar") * 1200L);
	}
	
	private void check() throws SQLException {
		PreparedStatement check = con.prepareStatement("SELECT * FROM `pendentes` WHERE `nick` = ?");
		PreparedStatement delete = con.prepareStatement("DELETE FROM `pendentes` WHERE `codigo` = ?");
		PreparedStatement log = con.prepareStatement("INSERT INTO `log` (`nick`, `produto`, `codigo`, `horario`)"
				+ " VALUES (?, ?, ?, ?)");
		
		for (Player p : Bukkit.getOnlinePlayers()) {
			// Checar se há algo pendente para ativar (para cada player)
			check.setString(1, p.getName());
			ResultSet rs = check.executeQuery();
			
			if (rs.next()) {
				String code = rs.getString("codigo");
				int productCode = rs.getInt("produto");
				String product = Integer.toString(productCode);
				
				if (getConfig().isList(product + ".commands")) {
					// Verificar se é necessário estar com o inventário vazio
					if (getConfig().getBoolean(product + ".empty-inventory") && !(isEmpty(p))) {
						p.sendMessage(getConfig().getString("msg-inv").replace('&', '§'));
						continue;
					}
					
					// Deletar de "pendentes"
					delete.setString(1, code);
					delete.executeUpdate();
					
					// Log
					log.setString(1, p.getName());
					log.setInt(2, productCode);
					log.setString(3, code);
					log.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
					log.executeUpdate();
					
					// Executar os comandos
					for (String cmd : getConfig().getStringList(product + ".commands"))
						Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("@player", p.getName()));
				}
			}
		}
	}
	
	private void checkChargeback() throws SQLException {
		PreparedStatement check = con.prepareStatement("SELECT * FROM `estornados`");
		PreparedStatement delete = con.prepareStatement("DELETE FROM `estornados` WHERE `codigo` = ?");
		
		ResultSet rs = check.executeQuery();
		
		// Verificar se algum pagamento foi estornado
		while (rs.next()) {
			String player = rs.getString("nick");
			String code = rs.getString("codigo");
			
			// Deletar de "estornados"
			delete.setString(1, code);
			delete.executeUpdate();
			
			// Executar comandos
			for (String cmd : getConfig().getStringList("comandos-chargeback"))
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("@player", player));
		}
	}
	
	private boolean isEmpty(Player p) {
		for (ItemStack is : p.getInventory().getContents())
			if ((is != null) && (is.getType() != Material.AIR))
				return false;
		
		for (ItemStack is : p.getEquipment().getArmorContents())
			if ((is != null) && (is.getType() != Material.AIR))
				return false;
		
		return true;
	}
	
}
