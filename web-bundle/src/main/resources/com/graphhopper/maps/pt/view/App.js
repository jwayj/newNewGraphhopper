/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import Sidebar from "./sidebar/Sidebar.js";
import Map from "./map/Map.js";
import {CreateQuery, ParseQuery, TimeOption} from "../data/Query.js";
import Path from "../data/Path.js";

export default class App extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            info: null,
            from: null,
            to: null,
            departureDateTime: new moment(),
            accessProfile: "foot",
            betaAccessTime: 1.0,
            egressProfile: "foot",
            rangeQuery: false,
            betaEgressTime: 1.0,
            rangeQueryDuration: "PT120M",
            limitStreetTime: "PT30M",
            ignoreTransfers: false,
            timeOption: TimeOption.DEPARTURE,
            distance : null, // 새로 추가한 부분
            routes: {
                query: null,
                isFetching: false
            }
        };
        ParseQuery(this.state, new URL(window.location).searchParams);
    }

    // handleDistanceChange = (distance) => {
    //     this.setState({ distance }); // 거리 값을 상태로 저장
    // };
    
    handleDistanceChange = (distance) => {
        this.setState({ distance }, () => {
            console.log("Distance updated in App state:", this.state.distance);
            this.updateQuery(); // 거리 값 변경 시 URL 생성
        });
    };

    updateQuery = () => {
        const queryUrl = CreateQuery("/route", this.state); // CreateQuery 함수 호출
        console.log("Generated URL:", queryUrl);
        window.history.replaceState(null, "", queryUrl); // URL 업데이트
    };

    componentDidMount() {
        window.fetch("/info")
            .then(response => response.ok ? response.json() : Promise.reject(response.statusText))
            .then(info => this.setState({info: info}))
            .catch(error => console.log("Error in Webrequest. Code: " + error));
    }

    componentDidUpdate(prevProps, prevState) {
        if (this.state.info !== null) { // Maybe better: Create a wrapper component that only renders this one when info is ready
            if (this.state.from !== null && this.state.to !== null && this.state.distance !==null&&this.state.distance !== prevState.distance) { // The only ways our state would not correspond to a valid query
                let query = CreateQuery(new URL("/route", window.location), this.state);
                let appQuery = CreateQuery(window.location, this.state);
                if (this.state.routes.query !== query) {
                    // What we are currently seeing or fetching is not want we want to see.
                    // So we make a request.
                    console.log(query);
                    this.setState({
                        routes: {
                            query: query,
                            isFetching: true
                        }
                    });
                    window.fetch(query)
                        .then(response => response.ok ? response.json() : Promise.reject())
                        .then(ghResponse => {
                            this.setState(prevState => {
                                if (CreateQuery(new URL("/route", window.location), prevState) !== query) return {}; // This reply is not what we want to know anymore
                                console.log(ghResponse);
                                const paths = ghResponse.paths.map(path => new Path(path));
                                const selectedPath = this._selectPathOnReceive(paths);
                                return {
                                    routes: {
                                        query: query,
                                        paths: paths,
                                        isLastQuerySuccess: true,
                                        isFetching: false,
                                        selectedRouteIndex: selectedPath
                                    }
                                };
                            });
                        })
                        .catch(() => this.setState({
                            routes: {
                                query: query,
                                isFetching: false,
                                isLastQuerySuccess: false
                            }
                        }));
                    window.history.replaceState({
                        name: "last state"
                    }, "", appQuery);
                }
            }
        }
    }

    _selectPathOnReceive(paths) {
        for (let i = 0; i < paths.length; i++) {
            let path = paths[i];
            if (path.isPossible) {
                path.isSelected = true;
                return i;
            }
        }
        return -1;
    }

    render() {
        if (this.state.info === null) return null;
        else return React.createElement("div", {
            className: "appWrapper"
        }, React.createElement("div", {
            className: "sidebar"
        }, React.createElement(Sidebar, {
            routes: this.state.routes,
            search: this.state,
            onSearchChange: e => this.setState(e),
            onSelectIndex: i => this.setState(prevState => ({routes: this._selectRoute(prevState.routes, i)}))
        }),
            React.createElement(Search, {
            points: [this.state.from, this.state.to], // 출발지와 도착지 전달
            map: null, // 현재는 null, 필요하면 수정
            onDistanceChange: (distance) => this.handleDistanceChange(distance) // 거리 값 변경 핸들러 전달
        })
        ), React.createElement("div", {
            className: "map"
        }, React.createElement(Map, {
            info: this.state.info,
            routes: this.state.routes,
            from: this.state.from,
            to: this.state.to,
            onSubmit: e => this.setState(e)
        })));
    }

    _selectRoute(oldState, newSelectedRouteIndex) {
        if (oldState.selectedRouteIndex >= 0) oldState.paths[oldState.selectedRouteIndex].isSelected = false;
        oldState.paths[newSelectedRouteIndex].isSelected = true;
        oldState.selectedRouteIndex = newSelectedRouteIndex;
        return oldState;
    }

}