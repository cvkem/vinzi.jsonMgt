package vinzi.jsonMgt.hib;

public class TrackInfo {
    public Long id;
    public Long getId() { return id;}
    protected void setId(Long id)  {this.id = id; };


    public String file_location;
    public String getFile_location() { return file_location;}
    public void setFile_location(String file_location)  {this.file_location = file_location; };


    public String track_name;
    public String getTrack_name() { return track_name;}
    public void setTrack_name(String track_name)  {this.track_name = track_name; };


    public TrackInfo() {};

    public TrackInfo(Long id, String file_location, String track_name) {
	this.id = id;
	this.file_location = file_location;
	this.track_name = track_name;
	return;};

   public String toString() {
        return " id=" + this.id + " file_location=" + this.file_location + " track_name=" + this.track_name;};

};
