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


    public String user;
    public String getUser() { return user;}
    public void setUser(String user)  {this.user = user; };


    public actionEntry() {};

    public actionEntry(Long id, java.util.Date datetime, String track, String action, String user) {
	this.id = id;
	this.datetime = datetime;
	this.track = track;
	this.action = action;
	this.user = user;
	return;};

   public String toString() {
        return " id=" + this.id + " datetime=" + this.datetime + " track=" + this.track + " action=" + this.action + " user=" + this.user;};

};
