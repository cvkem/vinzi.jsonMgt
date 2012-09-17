package vinzi.jsonMgt.hib;

public class actionEntry {
    public Long id;
    public Long getId() { return id;}
    protected void setId(Long id)  {this.id = id; };


    public java.util.Date datetime;
    public java.util.Date getDatetime() { return datetime;}
    public void setDatetime(java.util.Date datetime)  {this.datetime = datetime; };


    public String track;
    public String getTrack() { return track;}
    public void setTrack(String track)  {this.track = track; };


    public String action;
    public String getAction() { return action;}
    public void setAction(String action)  {this.action = action; };


    public String d_user;
    public String getD_user() { return d_user;}
    public void setD_user(String d_user)  {this.d_user = d_user; };


    public actionEntry() {};

    public actionEntry(Long id, java.util.Date datetime, String track, String action, String d_user) {
	this.id = id;
	this.datetime = datetime;
	this.track = track;
	this.action = action;
	this.d_user = d_user;
	return;};

   public String toString() {
        return " id=" + this.id + " datetime=" + this.datetime + " track=" + this.track + " action=" + this.action + " d_user=" + this.d_user;};

};
