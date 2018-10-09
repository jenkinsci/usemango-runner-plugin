package it.infuse.jenkins.usemango.enums;

public enum UseMangoNodeLabel {

	USEMANGO;
	
	@Override
    public String toString() {
        return name().toLowerCase();
    }

}