package jsqueak.util;

public class HexUtils {

	public static String translateRawData(int[] idata) {
		String hex = "";
		for (int i : idata) {
			String intHex = Integer.toHexString(i);
			hex += beautifyHex(intHex);
		}
		return hex;
	}

	private static String beautifyHex(String intHex) {
		if (intHex.isEmpty()) {
			return "";
		} else {
			String result = "";
			String hex = (intHex.length() < 2) ? "0" + intHex : intHex;
			for (int p = 0; p < hex.length(); p += 2) {
				String oneHex = hex.substring(p,	Math.min(p + 2, hex.length()));
				int intValue = Integer.parseInt(oneHex, 16);
				if (intValue >= 32 && intValue <= 127)
					result += (char) intValue;
				else
					result += "<" + oneHex + ">";
			}
			return result;
		}
	}

}
