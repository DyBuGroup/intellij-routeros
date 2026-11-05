/ip firewall filter
add action=drop chain=forward comment="defconf: drop all from WAN not DSTNATed" \
    connection-nat-state=!dstnat connection-state=new in-interface-list=WAN
