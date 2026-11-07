private JSONArray lastFixtures(int teamId) throws Exception {
    JSONObject j = request("fixtures?team=" + teamId
            + "&last=5&timezone=Europe%2FOslo");

    JSONArray a = j.optJSONArray("response");

    if (a == null || a.length() == 0) {
        j = request("fixtures?team=" + teamId
                + "&last=10&status=FT&timezone=Europe%2FOslo");
        a = j.optJSONArray("response");
    }

    return a == null ? new JSONArray() : a;
}
