/interface bridge
add name=bridge1 vlan-filtering=yes
/ip address
add address=192.168.88.1/24 interface=bridge1 comment="LAN #1"
