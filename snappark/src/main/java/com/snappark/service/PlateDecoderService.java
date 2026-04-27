package com.snappark.service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlateDecoderService {

    private static PlateDecoderService instance;
    private PlateDecoderService() {}
    public static synchronized PlateDecoderService getInstance() {
        if (instance == null) instance = new PlateDecoderService();
        return instance;
    }

    // ── RESULT ──────────────────────────────────────────────────────
    public static class PlateResult {
        public final boolean valid;
        public final String  formatted;
        public final String  state;
        public final String  rto;
        public final String  stateCode;
        public final int     rtoNum;
        public final String  error;
        public final String  stateColor;  // hex

        public PlateResult(boolean valid, String formatted, String state,
                           String rto, String stateCode, int rtoNum,
                           String error, String stateColor) {
            this.valid=valid; this.formatted=formatted; this.state=state;
            this.rto=rto; this.stateCode=stateCode; this.rtoNum=rtoNum;
            this.error=error; this.stateColor=stateColor;
        }
        public static PlateResult invalid(String error) {
            return new PlateResult(false,"","","","",0,error,"#334155");
        }
    }

    // ── STATE MAP ───────────────────────────────────────────────────
    private static final Map<String,String[]> STATES = new HashMap<>();
    static {
        // [Full State Name, Brand Color]
        STATES.put("AN", new String[]{"Andaman & Nicobar","#005F73"});
        STATES.put("AP", new String[]{"Andhra Pradesh",   "#0077B6"});
        STATES.put("AR", new String[]{"Arunachal Pradesh","#2D6A4F"});
        STATES.put("AS", new String[]{"Assam",            "#168AAD"});
        STATES.put("BR", new String[]{"Bihar",            "#E76F51"});
        STATES.put("CH", new String[]{"Chandigarh",       "#457B9D"});
        STATES.put("CG", new String[]{"Chhattisgarh",     "#6A4C93"});
        STATES.put("DD", new String[]{"Daman & Diu",      "#4361EE"});
        STATES.put("DL", new String[]{"Delhi",            "#E63946"});
        STATES.put("DN", new String[]{"Dadra & Nagar Haveli","#3A86FF"});
        STATES.put("GA", new String[]{"Goa",              "#F4A261"});
        STATES.put("GJ", new String[]{"Gujarat",          "#2A9D8F"});
        STATES.put("HR", new String[]{"Haryana",          "#264653"});
        STATES.put("HP", new String[]{"Himachal Pradesh", "#43AA8B"});
        STATES.put("JK", new String[]{"Jammu & Kashmir",  "#90E0EF"});
        STATES.put("JH", new String[]{"Jharkhand",        "#BB3E03"});
        STATES.put("KA", new String[]{"Karnataka",        "#C1121F"});
        STATES.put("KL", new String[]{"Kerala",           "#606C38"});
        STATES.put("LA", new String[]{"Ladakh",           "#8ECAE6"});
        STATES.put("LD", new String[]{"Lakshadweep",      "#023E8A"});
        STATES.put("MP", new String[]{"Madhya Pradesh",   "#D62828"});
        STATES.put("MH", new String[]{"Maharashtra",      "#FF6B35"});
        STATES.put("MN", new String[]{"Manipur",          "#7209B7"});
        STATES.put("ML", new String[]{"Meghalaya",        "#3A0CA3"});
        STATES.put("MZ", new String[]{"Mizoram",          "#4CC9F0"});
        STATES.put("NL", new String[]{"Nagaland",         "#F72585"});
        STATES.put("OD", new String[]{"Odisha",           "#FB8500"});
        STATES.put("PY", new String[]{"Puducherry",       "#B5179E"});
        STATES.put("PB", new String[]{"Punjab",           "#FFB703"});
        STATES.put("RJ", new String[]{"Rajasthan",        "#E9C46A"});
        STATES.put("SK", new String[]{"Sikkim",           "#80B918"});
        STATES.put("TN", new String[]{"Tamil Nadu",       "#1B4332"});
        STATES.put("TS", new String[]{"Telangana",        "#E9D8A6"});
        STATES.put("TR", new String[]{"Tripura",          "#9B2226"});
        STATES.put("UP", new String[]{"Uttar Pradesh",    "#AE2012"});
        STATES.put("UK", new String[]{"Uttarakhand",      "#94D2BD"});
        STATES.put("WB", new String[]{"West Bengal",      "#005F73"});
    }

    // ── RTO MAP (major RTOs per state) ──────────────────────────────
    private static final Map<String, Map<Integer,String>> RTO_MAP = new HashMap<>();
    static {
        // Maharashtra
        Map<Integer,String> mh = new HashMap<>();
        mh.put(1,"Mumbai Central"); mh.put(2,"Mumbai East"); mh.put(3,"Thane");
        mh.put(4,"Thane Rural"); mh.put(5,"Kalyan"); mh.put(6,"Raigad");
        mh.put(7,"Panvel"); mh.put(8,"Nashik"); mh.put(9,"Nashik Rural");
        mh.put(10,"Ahmednagar"); mh.put(11,"Pune City"); mh.put(12,"Pune");
        mh.put(13,"Satara"); mh.put(14,"Sangli"); mh.put(15,"Kolhapur");
        mh.put(16,"Solapur"); mh.put(17,"Osmanabad"); mh.put(18,"Latur");
        mh.put(19,"Nanded"); mh.put(20,"Aurangabad"); mh.put(21,"Jalna");
        mh.put(22,"Parbhani"); mh.put(23,"Beed"); mh.put(24,"Buldhana");
        mh.put(25,"Akola"); mh.put(26,"Amravati"); mh.put(27,"Yavatmal");
        mh.put(28,"Wardha"); mh.put(29,"Nagpur"); mh.put(30,"Nagpur Rural");
        mh.put(31,"Bhandara"); mh.put(32,"Chandrapur"); mh.put(33,"Gadchiroli");
        mh.put(34,"Gondia"); mh.put(35,"Washim"); mh.put(36,"Hingoli");
        mh.put(37,"Ratnagiri"); mh.put(38,"Sindhudurg"); mh.put(39,"Dhule");
        mh.put(40,"Nandurbar"); mh.put(41,"Jalgaon"); mh.put(43,"Pimpri-Chinchwad");
        mh.put(47,"Navi Mumbai"); mh.put(48,"Mumbai South"); mh.put(49,"Mumbai West");
        RTO_MAP.put("MH", mh);

        // Delhi
        Map<Integer,String> dl = new HashMap<>();
        dl.put(1,"Central Delhi"); dl.put(2,"East Delhi"); dl.put(3,"North Delhi");
        dl.put(4,"North East Delhi"); dl.put(5,"North West Delhi"); dl.put(6,"Rohini");
        dl.put(7,"South Delhi"); dl.put(8,"South West Delhi"); dl.put(9,"West Delhi");
        dl.put(10,"Vasant Vihar"); dl.put(11,"Dwarka"); dl.put(12,"Saket");
        RTO_MAP.put("DL", dl);

        // Karnataka
        Map<Integer,String> ka = new HashMap<>();
        ka.put(1,"Bengaluru Central"); ka.put(2,"Bengaluru East"); ka.put(3,"Bengaluru North");
        ka.put(4,"Bengaluru South"); ka.put(5,"Bengaluru West"); ka.put(6,"Kolar");
        ka.put(7,"Tumkur"); ka.put(8,"Mysuru"); ka.put(9,"Mandya"); ka.put(10,"Hassan");
        ka.put(11,"Chikkamagaluru"); ka.put(12,"Dakshina Kannada"); ka.put(13,"Udupi");
        ka.put(14,"Shivamogga"); ka.put(15,"Davanagere"); ka.put(16,"Chitradurga");
        ka.put(17,"Belagavi"); ka.put(18,"Dharwad"); ka.put(19,"Gadag"); ka.put(20,"Haveri");
        ka.put(21,"Uttara Kannada"); ka.put(22,"Vijayapura"); ka.put(23,"Bagalkot");
        ka.put(24,"Raichur"); ka.put(25,"Bellary"); ka.put(26,"Koppal"); ka.put(27,"Gulbarga");
        ka.put(28,"Yadgir"); ka.put(29,"Bidar"); ka.put(30,"Chamarajanagar");
        RTO_MAP.put("KA", ka);

        // Tamil Nadu
        Map<Integer,String> tn = new HashMap<>();
        tn.put(1,"Chennai Central"); tn.put(2,"Chennai North"); tn.put(3,"Chennai South");
        tn.put(4,"Chennai West"); tn.put(5,"Kancheepuram"); tn.put(6,"Tiruvallur");
        tn.put(7,"Vellore"); tn.put(8,"Thiruvannamalai"); tn.put(9,"Salem"); tn.put(10,"Namakkal");
        tn.put(11,"Dharmapuri"); tn.put(12,"Krishnagiri"); tn.put(13,"Coimbatore");
        tn.put(14,"Tiruppur"); tn.put(15,"Erode"); tn.put(16,"Nilgiris"); tn.put(17,"Madurai");
        tn.put(18,"Dindigul"); tn.put(19,"Theni"); tn.put(20,"Virudhunagar");
        tn.put(21,"Ramanathapuram"); tn.put(22,"Tirunelveli"); tn.put(23,"Thoothukudi");
        tn.put(24,"Kanyakumari"); tn.put(25,"Tiruchirappalli"); tn.put(26,"Karur");
        tn.put(27,"Perambalur"); tn.put(28,"Ariyalur"); tn.put(29,"Cuddalore");
        tn.put(30,"Villupuram"); tn.put(31,"Pondicherry"); tn.put(32,"Thanjavur");
        tn.put(33,"Nagapattinam"); tn.put(34,"Tiruvarur"); tn.put(35,"Pudukkottai");
        tn.put(36,"Sivaganga"); tn.put(37,"Kanchipuram West"); tn.put(38,"Avadi");
        RTO_MAP.put("TN", tn);

        // Gujarat
        Map<Integer,String> gj = new HashMap<>();
        gj.put(1,"Ahmedabad"); gj.put(2,"Ahmedabad Rural"); gj.put(3,"Gandhinagar");
        gj.put(4,"Mehsana"); gj.put(5,"Patan"); gj.put(6,"Banaskantha");
        gj.put(7,"Sabarkantha"); gj.put(8,"Kheda"); gj.put(9,"Anand"); gj.put(10,"Vadodara");
        gj.put(11,"Bharuch"); gj.put(12,"Narmada"); gj.put(13,"Surat"); gj.put(14,"Navsari");
        gj.put(15,"Tapi"); gj.put(16,"Dangs"); gj.put(17,"Valsad"); gj.put(18,"Rajkot");
        gj.put(19,"Surendranagar"); gj.put(20,"Morbi"); gj.put(21,"Jamnagar");
        gj.put(22,"Devbhoomi Dwarka"); gj.put(23,"Porbandar"); gj.put(24,"Junagadh");
        gj.put(25,"Gir Somnath"); gj.put(26,"Amreli"); gj.put(27,"Bhavnagar");
        gj.put(28,"Botad"); gj.put(29,"Dahod"); gj.put(30,"Chhota Udaipur");
        RTO_MAP.put("GJ", gj);

        // Rajasthan
        Map<Integer,String> rj = new HashMap<>();
        rj.put(1,"Jaipur"); rj.put(2,"Jaipur Rural"); rj.put(3,"Alwar"); rj.put(4,"Bharatpur");
        rj.put(5,"Dausa"); rj.put(6,"Sawai Madhopur"); rj.put(7,"Tonk"); rj.put(8,"Ajmer");
        rj.put(9,"Beawar"); rj.put(10,"Bhilwara"); rj.put(11,"Chittorgarh");
        rj.put(12,"Rajsamand"); rj.put(13,"Udaipur"); rj.put(14,"Dungarpur");
        rj.put(15,"Banswara"); rj.put(16,"Kota"); rj.put(17,"Bundi"); rj.put(18,"Baran");
        rj.put(19,"Jhalawar"); rj.put(20,"Sikar"); rj.put(21,"Jhunjhunu"); rj.put(22,"Churu");
        rj.put(23,"Nagaur"); rj.put(24,"Pali"); rj.put(25,"Jodhpur"); rj.put(26,"Barmer");
        rj.put(27,"Jaisalmer"); rj.put(28,"Jalore"); rj.put(29,"Sirohi"); rj.put(30,"Bikaner");
        rj.put(31,"Ganganagar"); rj.put(32,"Hanumangarh"); rj.put(33,"Dholpur");
        rj.put(34,"Karauli"); rj.put(35,"Pratapgarh");
        RTO_MAP.put("RJ", rj);

        // Uttar Pradesh
        Map<Integer,String> up = new HashMap<>();
        up.put(1,"Lucknow"); up.put(2,"Unnao"); up.put(3,"Raebareli"); up.put(4,"Sitapur");
        up.put(5,"Hardoi"); up.put(6,"Lakhimpur Kheri"); up.put(7,"Agra"); up.put(8,"Firozabad");
        up.put(9,"Mathura"); up.put(10,"Aligarh"); up.put(11,"Etah"); up.put(12,"Mainpuri");
        up.put(13,"Allahabad"); up.put(14,"Fatehpur"); up.put(15,"Pratapgarh");
        up.put(16,"Kanpur"); up.put(17,"Jhansi"); up.put(18,"Lalitpur"); up.put(19,"Jalaun");
        up.put(20,"Varanasi"); up.put(21,"Mirzapur"); up.put(22,"Sonbhadra");
        up.put(23,"Gorakhpur"); up.put(24,"Meerut"); up.put(25,"Moradabad");
        up.put(26,"Bareilly"); up.put(27,"Rampur"); up.put(28,"Shahjahanpur");
        up.put(29,"Pilibhit"); up.put(30,"Muzaffarnagar");
        RTO_MAP.put("UP", up);

        // West Bengal
        Map<Integer,String> wb = new HashMap<>();
        wb.put(1,"Kolkata"); wb.put(2,"Howrah"); wb.put(3,"Hooghly"); wb.put(4,"North 24 Parganas");
        wb.put(5,"South 24 Parganas"); wb.put(6,"Midnapore East"); wb.put(7,"Midnapore West");
        wb.put(8,"Bankura"); wb.put(9,"Burdwan"); wb.put(10,"Birbhum");
        wb.put(11,"Murshidabad"); wb.put(12,"Nadia"); wb.put(13,"Malda");
        wb.put(14,"Dinajpur North"); wb.put(15,"Dinajpur South"); wb.put(16,"Jalpaiguri");
        wb.put(17,"Darjeeling"); wb.put(18,"Cooch Behar"); wb.put(19,"Alipurduar");
        RTO_MAP.put("WB", wb);

        // Telangana
        Map<Integer,String> ts = new HashMap<>();
        ts.put(1,"Hyderabad"); ts.put(2,"Hyderabad East"); ts.put(3,"Ranga Reddy");
        ts.put(4,"Medchal"); ts.put(5,"Sangareddy"); ts.put(6,"Vikarabad");
        ts.put(7,"Medak"); ts.put(8,"Siddipet"); ts.put(9,"Nizamabad"); ts.put(10,"Nizamabad Rural");
        ts.put(11,"Karimnagar"); ts.put(12,"Peddapalli"); ts.put(13,"Jagtial");
        ts.put(14,"Rajanna Sircilla"); ts.put(15,"Warangal Urban"); ts.put(16,"Warangal Rural");
        ts.put(17,"Jayashankar Bhupalpally"); ts.put(18,"Mahabubabad"); ts.put(19,"Khammam");
        ts.put(20,"Bhadradri Kothagudem"); ts.put(21,"Nalgonda"); ts.put(22,"Suryapet");
        ts.put(23,"Yadadri Bhuvanagiri"); ts.put(24,"Nagarkurnool"); ts.put(25,"Mahabubnagar");
        ts.put(26,"Wanaparthy"); ts.put(27,"Gadwal"); ts.put(28,"Nirmal"); ts.put(29,"Adilabad");
        ts.put(30,"Mancherial"); ts.put(31,"Kumuram Bheem Asifabad"); ts.put(32,"Mulugu");
        RTO_MAP.put("TS", ts);

        // Add fallback for remaining states with basic RTOs
        for (String code : new String[]{"AN","AP","AR","AS","BR","CH","CG","DD","DN",
                "GA","HR","HP","JK","JH","KL","LA","LD","MP","MN","ML","MZ",
                "NL","OD","PY","PB","SK","TR","UK"}) {
            if (!RTO_MAP.containsKey(code)) {
                Map<Integer,String> basic = new HashMap<>();
                for (int i = 1; i <= 20; i++) basic.put(i, "RTO " + code + "-" + String.format("%02d", i));
                RTO_MAP.put(code, basic);
            }
        }
    }

    // ── PLATE FORMAT ────────────────────────────────────────────────
    // Standard: XX-00-XX-0000  (e.g. MH-12-AB-1234)
    // BH series: 00-BH-0000-XX (e.g. 22BH0001AB)
    private static final Pattern STANDARD = Pattern.compile(
        "^([A-Z]{2})(\\d{1,2})([A-Z]{1,3})(\\d{1,4})$"
    );
    private static final Pattern BH_SERIES = Pattern.compile(
        "^(\\d{2})BH(\\d{4})([A-Z]{1,2})$"
    );

    // ── DECODE ──────────────────────────────────────────────────────
    public PlateResult decode(String rawInput) {
        if (rawInput == null || rawInput.isBlank())
            return PlateResult.invalid("Enter license plate");

        String plate = rawInput.toUpperCase().replaceAll("[\\s\\-]", "");

        if (plate.length() < 6)
            return PlateResult.invalid("Too short — minimum 6 characters");
        if (plate.length() > 12)
            return PlateResult.invalid("Too long — maximum 10 characters");

        // BH series check
        Matcher bh = BH_SERIES.matcher(plate);
        if (bh.matches()) {
            int year = Integer.parseInt(bh.group(1));
            String formatted = bh.group(1) + " BH " + bh.group(2) + " " + bh.group(3);
            return new PlateResult(true, formatted,
                "Bharat Series (All India)", "BH-Series National Registration",
                "BH", year, null, "#3A86FF");
        }

        // Standard plate
        Matcher m = STANDARD.matcher(plate);
        if (!m.matches()) {
            if (plate.length() >= 2) {
                String sc = plate.substring(0, 2);
                if (!STATES.containsKey(sc))
                    return PlateResult.invalid("Unknown state code: " + sc);
            }
            return PlateResult.invalid("Invalid format — expected: XX00XX0000");
        }

        String stateCode = m.group(1);
        int    rtoNum    = Integer.parseInt(m.group(2));
        String letters   = m.group(3);
        String nums      = m.group(4);

        if (!STATES.containsKey(stateCode))
            return PlateResult.invalid("Unknown state code: " + stateCode);

        String[] stateInfo  = STATES.get(stateCode);
        String   stateName  = stateInfo[0];
        String   stateColor = stateInfo[1];

        Map<Integer,String> rtos = RTO_MAP.get(stateCode);
        String rtoName = rtos != null && rtos.containsKey(rtoNum)
            ? rtos.get(rtoNum)
            : stateCode + "-" + String.format("%02d", rtoNum) + " Regional Office";

        String formatted = stateCode + "-" + String.format("%02d", rtoNum)
            + "-" + letters + "-" + nums;

        return new PlateResult(true, formatted, stateName, rtoName,
            stateCode, rtoNum, null, stateColor);
    }

    // ── PARTIAL DECODE (for live typing) ───────────────────────────
    public String getStateHint(String input) {
        if (input == null || input.length() < 2) return "";
        String sc = input.toUpperCase().replaceAll("[\\s\\-]","");
        if (sc.length() < 2) return "";
        String code = sc.substring(0, 2);
        String[] info = STATES.get(code);
        return info != null ? info[0] : "Unknown state";
    }
}